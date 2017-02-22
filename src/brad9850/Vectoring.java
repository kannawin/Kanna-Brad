package brad9850;

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
 * Vectoring methods to get around, as well as helpers
 * 
 * @author Scott Kannawin & Christopher Bradford
 *
 */
public class Vectoring {
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

				int freeRadius = (int) (firstNode.getRadius() * 1.4);

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

	/**
	 * Work in progress turning function Returns a queue of time steps to move
	 * at full angular acceleration to aim at the target Currently is the
	 * equivalent of a slow moving sprinkler
	 * 
	 * @param space
	 * @param target
	 * @param ship
	 * @return
	 */
	@SuppressWarnings("static-access")
	public static Queue<Integer> aimHelp(Toroidal2DPhysics space, AbstractObject target, Ship ship) {
		// use the function for distance covered, solve for t(time steps)
		// (1/2)*d = 2*vi*t + a*t^2

		// t = ((-vi) + sqrt(vi^2 - 4ad)) / 2a
		Queue<Integer> timesteps = new LinkedList<Integer>();

		double vi = ship.getPosition().getAngularVelocity();
		double a = 3.5355339059;
		double angleA = ship.getPosition().getOrientation();

		Vector2D vectorA = new Vector2D();
		vectorA.fromAngle(angleA, 1000);

		// double d = space.findShortestDistanceVector(ship.getPosition(),
		// target.getPosition()).angleBetween(vectorA);

		double compensator = ship.getPosition().getOrientation();
		double compensateTo = angleBetween(space, ship, target);
		double d = compensator - compensateTo;

		// total time steps to get to the position
		double t = (((-(2 * vi)) + Math.sqrt(Math.abs((4 * vi * vi) - 4 * a * (.5 * d)))) / 2 * a);
		t = Math.ceil(t);
		// System.out.println(vi + "\t" + a + "\t" + d + "\t" + t);

		double aaa = adjustTurn(t, d);

		if (t < 38) {
			timesteps.add((int) t);
			timesteps.add((int) t * -1);
			timesteps.add(0);
		} else {
			timesteps.add((int) t * -1);
			timesteps.add((int) t);
			timesteps.add(0);
		}

		return timesteps;
	}

	public static double adjustTurn(double t, double d) {
		double alphaT = Math.ceil(Math.sqrt(d / 3.5355339059));
		double a = d / (alphaT * alphaT);
		// System.out.println(a);
		return a;
	}

	/**
	 * The vectoring agent behind the advanced movement method, returns a
	 * movement action that will go in the direction you want, towards the
	 * target, quickly
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @param velocity
	 * @return
	 */
	@SuppressWarnings("static-access") // Because Vector2D().fromAngle() cannot
										// be accessed in a static way
	public static AbstractAction nextVector(Toroidal2DPhysics space, Ship ship, AbstractObject target,
			double velocity) {
		// target self if can't resolve a target
		Vector2D direction = null;
		if (target == null) {
			target = ship;
		} else
			direction = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());

		Vector2D gotoPlace = new Vector2D();
		// use that angle for which it is going to accelerate, and set the
		// magnitude up
		if (target != ship)
			gotoPlace = gotoPlace.fromAngle(direction.getAngle(), velocity);
		else
			gotoPlace = new Vector2D(ship.getPosition());

		double compensator = ship.getPosition().getOrientation();
		double compensateTo = angleBetween(space, ship, target);
		double compensate = compensator - compensateTo + 2 * Math.PI;
		gotoPlace.rotate(compensate);

		// set the ship in motion
		AbstractAction sendOff = new MoveAction(space, ship.getPosition(), target.getPosition(), gotoPlace);
		return sendOff;
	}

	/**
	 * Advanced Movement Vector, slows down near the target if it shoot-able
	 * else it will get the right angle and finish movement Has a helper method
	 * below
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @param distanceFactor
	 * @return
	 */
	public static AbstractAction advancedMovementVector(Toroidal2DPhysics space, Ship ship, AbstractObject target,
			int distanceFactor) {
		// speed adjustments relative to max accel
		double movementFactor = 1.6;
		double movementMax = Movement.MAX_TRANSLATIONAL_ACCELERATION * movementFactor;

		AbstractAction sendOff = null;
		double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());

		// gets a set of non shootable asteroids
		Set<AbstractObject> asteroids = new HashSet<AbstractObject>();
		for (Asteroid obj : space.getAsteroids()) {
			if (!obj.isMineable()) {
				asteroids.add(obj);
			}
		}

		// will slow down if within the bounds of the distance, or it won't slow
		// down
		if (distance < distanceFactor) {
			double adjustedVelocity = (distance / distanceFactor) * (movementMax / (movementFactor * 1.25));

			if (target.getClass() == Beacon.class
					&& (Combat.willHitMovingTarget(space, ship, target, target.getPosition().getTranslationalVelocity())
							|| ship.getPosition().getTotalTranslationalVelocity() < movementMax * .1)) {

				sendOff = nextVector(space, ship, target, movementMax);
			} else {
				// TODO make a quick rotate and rotation compensator action
				// method for this
				sendOff = nextVector(space, ship, target, adjustedVelocity);
			}
		}
		// if path is clear it will go
		else if (space.isPathClearOfObstructions(ship.getPosition(), target.getPosition(), asteroids, 0)) {
			sendOff = nextVector(space, ship, target, movementMax);
		}

		// else it will find a new target
		else {
			sendOff = nextVector(space, ship, nextFreeVector(space, ship, target), movementMax);
		}

		return sendOff;
	}

	/**
	 * Helper function of the advanced vectoring function, it finds the next
	 * closest free object with a clear path of the same type that the original
	 * target was on
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	private static AbstractObject nextFreeVector(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		Set<AbstractObject> objSet = space.getAllObjects();
		ArrayList<AbstractObject> targetObjs = new ArrayList<AbstractObject>();

		double minDistance = Double.MAX_VALUE;
		AbstractObject gotoTarget = null;

		// get objects of the same type
		// TODO Adjust for shooting stuff, gathering resources, or getting
		// beacons
		for (AbstractObject obj : objSet) {
			if (obj.getClass() == target.getClass()) {
				targetObjs.add(obj);
			}
		}
		// collects all the asteroids you can't fly through
		Set<AbstractObject> nonShootable = new HashSet<AbstractObject>();
		for (Asteroid asteroid : space.getAsteroids()) {
			if (!asteroid.isMineable()) {
				nonShootable.add(asteroid);
			}
		}
		// adds bases as impassable objects too
		for (Base bases : space.getBases()) {
			nonShootable.add(bases);
		}

		// finds the shortest free path
		for (AbstractObject obj : targetObjs) {
			double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());
			if (distance < minDistance
					&& space.isPathClearOfObstructions(ship.getPosition(), obj.getPosition(), nonShootable, 2)) {
				minDistance = distance;
				gotoTarget = obj;
			}
		}

		return gotoTarget;
	}

	/**
	 * Gets the angle between two objects
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	public static double angleBetween(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		Vector2D pos1 = new Vector2D(ship.getPosition());
		Vector2D pos2 = new Vector2D(target.getPosition());

		double angle = pos1.angleBetween(pos2);

		return angle;
	}

}
