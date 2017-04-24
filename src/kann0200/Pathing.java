package kann0200;

import java.awt.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Methods for planning paths
 * 
 * @author Scott Kannawin & Christopher Bradford
 *
 */
public class Pathing {
	public static double VeryLargeValue = 99999999999.0;

	/**
	 * Get a path between a ship and its target that avoids obstructions between
	 * them
	 * 
	 * @param space
	 * @param target
	 * @param ship
	 * @return
	 */
	public static ArrayList<UUID> findPath(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		ArrayList<UUID> nodeList = makeNodes(space, ship, target);
		double[][] distanceMatrix = distanceBetweenNodes(space, nodeList);

		int[] parentNode = path_AStar(space, nodeList, distanceMatrix);
//		int[] parentNode = path_GBFS(space, nodeList, distanceMatrix);

		ArrayList<Integer> reversePath = new ArrayList<Integer>();
		// Start at the goal, and walk backward to the start
		int currentNode = 1;
		// Add nodes until we reach the root of the tree
		// (Don't add ship's position, since we're already there)
		while (currentNode > 0) {
			reversePath.add(currentNode);
			currentNode = parentNode[currentNode];
		}

		// Now turn the reverse path into a series of positions for our ship
		ArrayList<UUID> path = new ArrayList<UUID>();
		for (int i = reversePath.size() - 1; i >= 0; i--) {
			int nodeIndex = reversePath.get(i);
			path.add(nodeList.get(nodeIndex));
		}

		return path;
	}
	
	/**
	 * Gets the parents of nodes based on the greedy BFS algorithm
	 * 
	 * @param space
	 * @param nodeList
	 * @param distanceMatrix
	 * @return
	 */
	public static int[] path_GBFS(Toroidal2DPhysics space, ArrayList<UUID> nodeList, double[][] distanceMatrix) {
		int nodeCount = nodeList.size();
		int startIndex = 0;
		int goalIndex = 1;

		UUID goalID = nodeList.get(goalIndex);

		double[] heuristicList = getHeuristicValues(space, nodeList, goalID);

		int[] parentNode = new int[nodeCount];

		// Defaults to false
		boolean[] hasBeenVisited = new boolean[nodeCount];
		boolean[] inFrontier = new boolean[nodeCount];

		// Initialize values
		for (int i = 0; i < nodeCount; i++) {
			parentNode[i] = -1;
		}

		// Start GBFS
		int currentNode = startIndex;
		hasBeenVisited[currentNode] = true;
		

		while (currentNode != goalIndex) {
			// Find all unvisited nodes connected to the current node
			for (int i = 0; i < nodeCount; i++) {
				if (!hasBeenVisited[i] && (distanceMatrix[currentNode][i] >= 0)) {
					if(!inFrontier[i]){
						inFrontier[i] = true;
						parentNode[i] = currentNode;
					}
				}
			}
			
			//Find the next node to visit
			int bestNode = -1;
			double bestNodeScore = VeryLargeValue;
			for(int i = 0; i < nodeCount; i++){
				if(inFrontier[i]){
					double nodeScore = heuristicList[i];
					if(nodeScore < bestNodeScore){
						bestNode = i;
						bestNodeScore = nodeScore;
					}
				}
			}
			
			//If there were no more nodes, quit
			if(bestNode == -1){
				break;
			}
			
			//Update current node
			currentNode = bestNode;
			hasBeenVisited[bestNode] = true;
			inFrontier[bestNode] = false;
		}

		return parentNode;
	}

	/**
	 * Gets the parents of nodes based on the A* algorithm
	 * 
	 * @param space
	 * @param nodeList
	 * @param distanceMatrix
	 * @return
	 */
	public static int[] path_AStar(Toroidal2DPhysics space, ArrayList<UUID> nodeList, double[][] distanceMatrix) {
		int nodeCount = nodeList.size();
		int startIndex = 0;
		int goalIndex = 1;

		UUID goalID = nodeList.get(goalIndex);

		double[] heuristicList = getHeuristicValues(space, nodeList, goalID);
		double[] pathCostList = new double[nodeCount];

		int[] parentNode = new int[nodeCount];

		ArrayList<Integer> frontier = new ArrayList<Integer>();

		// Defaults to false
		boolean[] hasBeenVisited = new boolean[nodeCount];

		// Initialize values
		for (int i = 0; i < nodeCount; i++) {
			pathCostList[i] = VeryLargeValue;

			parentNode[i] = -1;
		}

		// Start A*
		int currentNode = startIndex;
		hasBeenVisited[currentNode] = true;
		pathCostList[currentNode] = 0;

		while (currentNode != goalIndex) {
			// Find all unvisited nodes connected to the current node
			for (int i = 0; i < nodeCount; i++) {
				if (!hasBeenVisited[i] && (distanceMatrix[currentNode][i] >= 0)) {
					// Add it to the frontier & update its best-path information
					updateFrontier(frontier, distanceMatrix, pathCostList, parentNode, currentNode, i);
				}
			}

			// If there are no more nodes to visit, then we should quit
			// searching
			if (frontier.size() == 0) {
				break;
			}

			// Find the node in the frontier with the lowest evaluation function
			int bestNode = 0;
			double bestNodeScore = VeryLargeValue;
			for (Integer nodeToCheck : frontier) {
				// f(x) = g(x) + h(x)
				double nodeScore = pathCostList[nodeToCheck] + heuristicList[nodeToCheck];
				if (nodeScore < bestNodeScore) {
					bestNode = nodeToCheck;
					bestNodeScore = nodeScore;
				}
			}

			// Visit that node
			currentNode = bestNode;
			hasBeenVisited[bestNode] = true;
			frontier.remove(frontier.indexOf(bestNode));
		}

		return parentNode;
	}

	/**
	 * Adds the node being checked to the frontier (if it isn't there already)
	 * and update its parent node. Modifies frontier, pathCostList, and
	 * parentNode
	 * 
	 * @param frontier
	 * @param heuristicList
	 * @param pathCostList
	 * @param evaluationList
	 * @param parentNode
	 */
	public static void updateFrontier(ArrayList<Integer> frontier, double[][] distanceMatrix, double[] pathCostList,
			int[] parentNode, int currentNode, int nodeToCheck) {
		boolean nodeIsInFrontier = false;
		for (Integer i : frontier) {
			if (i == nodeToCheck) {
				nodeIsInFrontier = true;
			}
		}

		// Add the node to the frontier if it isn't in there already
		if (!nodeIsInFrontier) {
			frontier.add(nodeToCheck);
		}

		// If the path cost through the current node is better than the previous
		// best path cost, mark the current node as the best way to get to the
		// node being checked.
		double pathCostThroughCurrentNode = pathCostList[currentNode] + distanceMatrix[currentNode][nodeToCheck];
		if (pathCostThroughCurrentNode < pathCostList[nodeToCheck]) {
			pathCostList[nodeToCheck] = pathCostThroughCurrentNode;
			parentNode[nodeToCheck] = currentNode;
		}
	}

	/**
	 * Make all of the nodes that will be used for pathing algorithms First node
	 * is always start, second node is always goal
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	public static ArrayList<UUID> makeNodes(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		ArrayList<UUID> nodeList = new ArrayList<UUID>();
		// If this is changed, be sure to always add ship first and target
		// second.

		// Add start and goal
		nodeList.add(ship.getId());
		nodeList.add(target.getId());

		// Add beacons and mineable asteroids
		for (Beacon energy : space.getBeacons()) {
			nodeList.add(energy.getId());
		}
		for (Asteroid mine : space.getAsteroids()) {
			if (mine.isMineable()) {
				nodeList.add(mine.getId());
			}
		}

		return nodeList;
	}

	/**
	 * Initializes the distance matrix
	 * 
	 * @param nodes
	 * @param space
	 * @param next
	 * @return
	 */
	public static double[][] distanceBetweenNodes(Toroidal2DPhysics space, ArrayList<UUID> nodes) {
		int numNodes = nodes.size();

		// Initialize distance matrix
		double[][] distanceMatrix = new double[numNodes][numNodes];
		for (int i = 0; i < numNodes; i++) {
			for (int j = 0; j < numNodes; j++) {
				distanceMatrix[i][j] = -1;
			}
		}

		Set<AbstractObject> obstructions = findObstructions(space);

		// Find the actual distance between nodes
		for (int i = 0; i < nodes.size() - 1; i++) {
			// Start at i + 1 so we don't check the same pair of nodes twice
			for (int j = i + 1; j < nodes.size(); j++) {
				AbstractObject firstNode = space.getObjectById(nodes.get(i));
				AbstractObject secondNode = space.getObjectById(nodes.get(j));

				int freeRadius = (int) (firstNode.getRadius() * 1.1);

				if (space.isPathClearOfObstructions(firstNode.getPosition(), secondNode.getPosition(), obstructions,
						freeRadius)) {
					double distance = space.findShortestDistance(firstNode.getPosition(), secondNode.getPosition());
					distanceMatrix[i][j] = distance;
					distanceMatrix[j][i] = distance;
				}
			}
		}

		return distanceMatrix;
	}

	/**
	 * Get the heuristic for the distance from the nodes to the goal
	 * 
	 * @param space
	 * @param nodes
	 * @param goalID
	 * @return
	 */
	public static double[] getHeuristicValues(Toroidal2DPhysics space, ArrayList<UUID> nodes, UUID goalID) {
		int numNodes = nodes.size();
		Position goalPosition = space.getObjectById(goalID).getPosition();

		double[] heuristicList = new double[numNodes];

		for (int i = 0; i < numNodes; i++) {
			Position currentNodePosition = space.getObjectById(nodes.get(i)).getPosition();
			heuristicList[i] = space.findShortestDistance(goalPosition, currentNodePosition);
		}

		return heuristicList;
	}

	/**
	 * Get all of the potential obstructions for the ship
	 * 
	 * @param space
	 * @return
	 */
	public static Set<AbstractObject> findObstructions(Toroidal2DPhysics space) {
		// Find all obstacles
		Set<AbstractObject> obstructions = new HashSet<AbstractObject>();
		for (Asteroid block : space.getAsteroids()) {
			if (!block.isMineable()) {
				obstructions.add(block);
			}
		}
		for (Base block : space.getBases()) {
			obstructions.add(block);
		}

		// Don't worry about pathing around other ships & bullets yet

		return obstructions;
	}
}
