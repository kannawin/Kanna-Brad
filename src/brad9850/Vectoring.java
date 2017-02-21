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
 * @author Scott Kannawin & Christopher Bradford
 *
 */
public class Vectoring {
	
	/**
	 * Movement map for getting around to the target selected using the Floyd-Warshall all pairs shortest path algorithm
	 * 
	 * @param space
	 * @param target
	 * @param ship
	 * @return
	 */
	public static ArrayList<UUID> movementMap(Toroidal2DPhysics space, AbstractObject target, Ship ship){
		ArrayList<UUID> movements = new ArrayList<UUID>();
		ArrayList<UUID> objectOrder = new ArrayList<UUID>();
		
		objectOrder.add(ship.getId());
		for(Beacon energy : space.getBeacons()){
			objectOrder.add(energy.getId());
		}
		for(Asteroid mine : space.getAsteroids()){
			if(mine.isMineable()){
				objectOrder.add(mine.getId());
			}
		}
		objectOrder.add(target.getId());
		
		int sizeOfMap = objectOrder.size();
		
		//initializes the distance matrix and next matrix
		int[][] next = nextLocation(objectOrder,space);
		int[][] dist = distanceToNext(objectOrder,space,next);
		
		//the actual algorithm itself
		for(int k = 0; k<sizeOfMap;k++){
			for(int i=0;i<sizeOfMap;i++){
				for(int j=0;j<sizeOfMap;j++){
					if((dist[i][k] + dist[k][j]) < dist[i][j]){
						dist[i][j] = dist[i][k] + dist[k][j];
						next[i][j] = next[i][k];
					}
				}
			}
		}
		
		ArrayList<Integer> path = path(0, (sizeOfMap - 1), next);
		if(path.size() > 0 && objectOrder.size() > 0){
			for(int i = 0; i < path.size(); i++){
				movements.add(objectOrder.get(path.get(i)));
			}
		}
		else{
			movements.add(ship.getId());
		}
		
		return movements;
	}
	
	/**
	 * path from a to be with the filled in matrix found from the algorithm
	 * 
	 * @param a
	 * @param b
	 * @param next
	 * @return
	 */
	public static ArrayList<Integer> path(int a, int b, int[][] next){
		ArrayList<Integer> temp = new ArrayList<Integer>();
		temp.add(a);
		while(a != b && next != null && b != -1 && a != -1){
			a = next[a][b];
			temp.add(a);
		}
		
		return temp;
	}
	
	/**
	 * Initializes the distance matrix
	 * 
	 * @param order
	 * @param space
	 * @param next
	 * @return
	 */
	public static int[][] distanceToNext(ArrayList<UUID> order,Toroidal2DPhysics space, int[][] next){
		int[][] distance = new int[next.length][next.length];
		for(int i = 0; i < next.length; i++){
			for(int j = 0; j < next.length; j++){
				distance[i][j] = 10000;
			}
		}
		for(int i = 0; i < order.size(); i++){
			for(int j = 0; j< order.size(); j++){
				if(next[i][j] != -1){
					//add the edge distance to the heuristic function
					//heuristic is the distance to the target
					int tempDist = (int) (space.findShortestDistance(space.getObjectById(order.get(i)).getPosition(),
							space.getObjectById(order.get(j)).getPosition()) + 
							space.findShortestDistance(space.getObjectById(order.get(j)).getPosition(),
									space.getObjectById(order.get(order.size() - 1)).getPosition()));
					
					//give slight priority to beacons
					if(space.getObjectById(order.get(i)).getClass() == Beacon.class)
						tempDist = tempDist / 2;
					
					
					if((tempDist/2) <= space.findShortestDistance(space.getObjectById(order.get(i)).getPosition(), space.getObjectById(order.get(j)).getPosition()))
						distance[i][j] = tempDist;
				}
			}
		}
		
		return distance;
	}
	
	/**
	 * Initializes the matrix which defines the next node
	 * if there is a non collectable on the way to the next target it will not go through there
	 * 
	 * @param order
	 * @param space
	 * @return
	 */
	public static int[][] nextLocation(ArrayList<UUID> order, Toroidal2DPhysics space){
		int[][] next = new int[order.size()][order.size()];
		for(int i = 0; i < order.size(); i++){
			for(int j = 0; j < order.size(); j++){
				next[i][j] = -1;
			}
		}
		
		Set<AbstractObject> obstruction = new HashSet<AbstractObject>();
		for(Asteroid block : space.getAsteroids()){
			if(!block.isMineable()){
				obstruction.add(block);
			}
		}
		for(Base block : space.getBases()){
			obstruction.add(block);
		}
		
		
		for(int i = 0; i < order.size(); i++){
			for(int j = 0; j < order.size(); j++){
				if(i != j && space.isPathClearOfObstructions(space.getObjectById(order.get(i)).getPosition(),
						space.getObjectById(order.get(j)).getPosition(),
						obstruction,
						(int) (space.getObjectById(order.get(0)).getRadius() * 1.4))){
					next[i][j] = j;
				}
			}
		}
		return next;
	}
	
	
	
	
	/**
	 * Work in progress turning function
	 * Returns a queue of time steps to move at full angular acceleration to aim at the target
	 * Currently is the equivalent of a slow moving sprinkler
	 * 
	 * @param space
	 * @param target
	 * @param ship
	 * @return
	 */
	@SuppressWarnings("static-access")
	public static Queue<Integer> aimHelp(Toroidal2DPhysics space, AbstractObject target, Ship ship){
		//use the function for distance covered, solve for t(time steps)
		//	(1/2)*d = 2*vi*t + a*t^2
		
		// 	t = ((-vi) + sqrt(vi^2 - 4ad)) / 2a
		Queue<Integer> timesteps = new LinkedList<Integer>();
		
		double vi = ship.getPosition().getAngularVelocity();
		double a = 3.5355339059;
		double angleA = ship.getPosition().getOrientation();
		
		Vector2D vectorA = new Vector2D();
		vectorA.fromAngle(angleA, 1000);

		
		//double d = space.findShortestDistanceVector(ship.getPosition(), target.getPosition()).angleBetween(vectorA);
		
		double compensator = ship.getPosition().getOrientation();
		double compensateTo = angleBetween(space,ship,target);
		double d = compensator - compensateTo;
		
		//total time steps to get to the position
		double t = (((-(2*vi)) + Math.sqrt(Math.abs((4*vi*vi) - 4 * a *(.5 * d)))) / 2 * a);
		t = Math.ceil(t);
		//System.out.println(vi + "\t" + a + "\t" + d + "\t" + t);
		
		double aaa = adjustTurn(t,d);
		
		if(t < 38){
			timesteps.add((int) t);
			timesteps.add((int) t * -1);
			timesteps.add(0);
		}
		else{
			timesteps.add((int) t * -1);
			timesteps.add((int) t);
			timesteps.add(0);
		}
		
		
		return timesteps;
	}
	
	public static double adjustTurn(double t, double d){
		double alphaT = Math.ceil(Math.sqrt(d/3.5355339059));
		double a = d/(alphaT*alphaT);
		//System.out.println(a);
		return a;
	}
	
	/**
	 * The vectoring agent behind the advanced movement method, returns a movement action that will go in the direction you want,
	 * towards the target, quickly
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @param velocity
	 * @return
	 */
	@SuppressWarnings("static-access") //Because Vector2D().fromAngle() cannot be accessed in a static way
	public static AbstractAction nextVector(Toroidal2DPhysics space, Ship ship, AbstractObject target, double velocity){
		//target self if can't resolve a target
		Vector2D direction = null;
		if(target == null){
			target = ship;
		}
		else
			direction = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());
		
		
		Vector2D gotoPlace = new Vector2D();
		//use that angle for which it is going to accelerate, and set the magnitude up
		if(target != ship)
			gotoPlace = gotoPlace.fromAngle(direction.getAngle(),velocity);
		else
			gotoPlace = new Vector2D(ship.getPosition());
		
		double compensator = ship.getPosition().getOrientation();
		double compensateTo = angleBetween(space,ship,target);
		double compensate = compensator - compensateTo + 2*Math.PI;
		gotoPlace.rotate(compensate);
		
		
		//set the ship in motion
		AbstractAction sendOff = new MoveAction(space ,ship.getPosition(), target.getPosition() , gotoPlace);
		return sendOff;	
	}
	

	/**
	 * Advanced Movement Vector, slows down near the target if it shoot-able else it will get the right angle and finish movement
	 * Has a helper method below
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @param distanceFactor
	 * @return
	 */
	public static AbstractAction advancedMovementVector(Toroidal2DPhysics space, Ship ship, AbstractObject target, int distanceFactor){
		//speed adjustments relative to max accel
		double movementFactor = 1.6;
		double movementMax = Movement.MAX_TRANSLATIONAL_ACCELERATION*movementFactor;
		
		AbstractAction sendOff = null;
		double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());
		
		
		//gets a set of non shootable asteroids
		Set<AbstractObject> asteroids = new HashSet<AbstractObject>();
		for(Asteroid obj : space.getAsteroids()){
			if(!obj.isMineable()){
				asteroids.add(obj);
			}
		}
		
		
		//will slow down if within the bounds of the distance, or it won't slow down
		if(distance < distanceFactor){
			double adjustedVelocity = (distance/distanceFactor) * (movementMax / (movementFactor*1.25));
			
			if(target.getClass() == Beacon.class &&
					(Combat.willHitMovingTarget(space,ship,target,target.getPosition().getTranslationalVelocity()) ||
							ship.getPosition().getTotalTranslationalVelocity() < movementMax*.1)){
				
				sendOff = nextVector(space,ship,target, movementMax);
			}
			else{
				//TODO make a quick rotate and rotation compensator action method for this
				sendOff = nextVector(space,ship,target, adjustedVelocity);
			}
		}
		//if path is clear it will go
		else if(space.isPathClearOfObstructions(ship.getPosition(), target.getPosition(), asteroids, 0)){
			sendOff = nextVector(space,ship,target,movementMax);
		}
		
		//else it will find a new target
		else{
			sendOff = nextVector(space,ship,nextFreeVector(space,ship,target), movementMax);
		}
		
		
		return sendOff;
	}
	
	
	/**
	 * Helper function of the advanced vectoring function, it finds the next closest free object with a clear path of the same 
	 * type that the original target was on
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	private static AbstractObject nextFreeVector(Toroidal2DPhysics space, Ship ship, AbstractObject target){
		Set<AbstractObject> objSet = space.getAllObjects();
		ArrayList<AbstractObject> targetObjs = new ArrayList<AbstractObject>();
		
		double minDistance = Double.MAX_VALUE;
		AbstractObject gotoTarget = null;
		
		//get objects of the same type
		//TODO Adjust for shooting stuff, gathering resources, or getting beacons
		for(AbstractObject obj : objSet){
			if (obj.getClass() == target.getClass()){
				targetObjs.add(obj);
			}
		}
		//collects all the asteroids you can't fly through
		Set<AbstractObject> nonShootable = new HashSet<AbstractObject>(); 
		for(Asteroid asteroid : space.getAsteroids()){
			if(!asteroid.isMineable()){
				nonShootable.add(asteroid);
			}
		}
		//adds bases as impassable objects too
		for(Base bases : space.getBases()){
			nonShootable.add(bases);
		}
		
		//finds the shortest free path
		for(AbstractObject obj : targetObjs){
			double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());
			if(distance < minDistance && space.isPathClearOfObstructions(ship.getPosition(), obj.getPosition(), nonShootable, 2)){
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
	public static double angleBetween(Toroidal2DPhysics space, Ship ship, AbstractObject target){
		Vector2D pos1 = new Vector2D(ship.getPosition());
		Vector2D pos2 = new Vector2D(target.getPosition());
		
		double angle = pos1.angleBetween(pos2);
		
		return angle;
	}
	

}
