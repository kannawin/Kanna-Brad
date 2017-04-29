package brad9850;

import java.awt.List;
import java.util.ArrayList;
import java.util.Arrays;
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
	public static ArrayList<Position> findPath(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		ArrayList<Position> nodeList = makeNodes(space, ship, target);
		double[][] distanceMatrix = distanceBetweenNodes(space, nodeList);

		int[] parentNode = path_AStar(space, nodeList, distanceMatrix);

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
		ArrayList<Position> path = new ArrayList<Position>();
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
	public static int[] path_GBFS(Toroidal2DPhysics space, ArrayList<Position> nodeList, double[][] distanceMatrix) {
		int nodeCount = nodeList.size();
		int startIndex = 0;
		int goalIndex = 1;

		Position goalPosition = nodeList.get(goalIndex);

		double[] heuristicList = getHeuristicValues(space, nodeList, goalPosition);

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
	public static int[] path_AStar(Toroidal2DPhysics space, ArrayList<Position> nodeList, double[][] distanceMatrix) {
		int nodeCount = nodeList.size();
		int startIndex = 0;
		int goalIndex = 1;

		Position goalPosition = nodeList.get(goalIndex);

		double[] heuristicList = getHeuristicValues(space, nodeList, goalPosition);
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
	public static ArrayList<Position> makeNodes(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		ArrayList<Position> nodeList = new ArrayList<Position>();
		// If this is changed, be sure to always add ship first and target
		// second.

		// Add start and goal
		nodeList.add(ship.getPosition());
		nodeList.add(target.getPosition());

		//Add pre-made nodes
		nodeList.addAll(getCTFNodes());

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
	public static double[][] distanceBetweenNodes(Toroidal2DPhysics space, ArrayList<Position> nodes) {
		int numNodes = nodes.size();

		// Initialize distance matrix
		double[][] distanceMatrix = new double[numNodes][numNodes];
		for (int i = 0; i < numNodes; i++) {
			for (int j = 0; j < numNodes; j++) {
				distanceMatrix[i][j] = -1;
			}
		}

		Set<AbstractObject> obstructions = findObstructions(space, nodes.get(1));

		// Find the actual distance between nodes
		for (int i = 0; i < nodes.size() - 1; i++) {
			// Start at i + 1 so we don't check the same pair of nodes twice
			for (int j = i + 1; j < nodes.size(); j++) {
				Position firstNodePosition = nodes.get(i);
				Position secondNodePosition = nodes.get(j);
				
				int freeRadius = (int) (Ship.SHIP_RADIUS * 1.1);

				if (space.isPathClearOfObstructions(firstNodePosition, secondNodePosition, obstructions, freeRadius)) {
					double distance = space.findShortestDistance(firstNodePosition, secondNodePosition);
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
	public static double[] getHeuristicValues(Toroidal2DPhysics space, ArrayList<Position> nodes, Position goalPosition) {
		int numNodes = nodes.size();

		double[] heuristicList = new double[numNodes];

		for (int i = 0; i < numNodes; i++) {
			Position currentNodePosition = nodes.get(i);
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
	public static Set<AbstractObject> findObstructions(Toroidal2DPhysics space, Position targetPosition) {
		// Find all obstacles
		Set<AbstractObject> obstructions = new HashSet<AbstractObject>();
		for (Asteroid block : space.getAsteroids()) {
			if (!block.isMineable()) {
				//Don't add an obstruction if it is our target
				if(!block.getPosition().equalsLocationOnly(targetPosition)){
					obstructions.add(block);
				}
			}
		}
		
		for (Base block : space.getBases()) {
			//Don't add an obstruction if it is our target
			if(!block.getPosition().equalsLocationOnly(targetPosition)){
				obstructions.add(block);
			}
		}

		// Don't worry about pathing around other ships & bullets
//		for (Ship ship : space.getShips()){
//			obstructions.add(ship);
//		}

		return obstructions;
	}
	
	public static ArrayList<Position> getCTFNodes(){
		return new ArrayList<>(Arrays.asList(new Position(840, 218),
											new Position(842, 123),
											new Position(803, 154),
											new Position(804, 96),
											new Position(874, 72),
											new Position(895, 19),
											new Position(1459, 8),
											new Position(1493, 91),
											new Position(1596, 128),
											new Position(1593, 448),
											new Position(1517, 473),
											new Position(1475, 540),
											new Position(1528, 621),
											new Position(1592, 655),
											new Position(1592, 954),
											new Position(1503, 980),
											new Position(71, 971),
											new Position(127, 10),
											new Position(97, 93),
											new Position(6, 123),
											new Position(722, 70),
											new Position(682, 10),
											new Position(798, 982),
											new Position(880, 1003),
											new Position(719, 1005),
											new Position(757, 216),
											new Position(753, 122),
											new Position(948, 539),
											new Position(652, 540),
											new Position(833, 882),
											new Position(833, 950),
											new Position(793, 920),
											new Position(757, 874),
											new Position(752, 954),
											new Position(1255, 250),
											new Position(1250, 808),
											new Position(1367, 703),
											new Position(1353, 898),
											new Position(1367, 150),
											new Position(1368, 361),
											new Position(1219, 537),
											new Position(1008, 323),
											new Position(1092, 71),
											new Position(1354, 66),
											new Position(1005, 807),
											new Position(1099, 977),
											new Position(1331, 993),
											new Position(583, 320),
											new Position(575, 794),
											new Position(488, 71),
											new Position(263, 66),
											new Position(220, 141),
											new Position(217, 350),
											new Position(341, 248),
											new Position(363, 534),
											new Position(229, 695),
											new Position(237, 903),
											new Position(329, 801),
											new Position(267, 983),
											new Position(496, 991),
											new Position(112, 538),
											new Position(68, 449),
											new Position(64, 630)));
	}
}
