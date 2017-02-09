package brad9850;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.actions.RawAction;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

public class Functions{
	
	
	/**
	 * See if a team is a non-AI enemy
	 * 
	 * @param teamName The name of the team we are checking
	 * @param friendlyTeamName The name of our team
	 */
	public static boolean isHumanEnemyTeam(String teamName, String friendlyTeamName){
		//See if it's our name
		if(teamName.equalsIgnoreCase(friendlyTeamName)){
			return false;
		}

		String[] aiNames = {"RandomTeam", "DoNothingTeam", "HeuristicTeam"};
		//See if it's an AI name
		for(String name: aiNames){
			if(teamName.equalsIgnoreCase(name)){
				return false;
			}
		}
		
		//Otherwise, it's a human enemy
		return true;
	}

	/**
	 * Find the base for an enemy team nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	public static AbstractActionableObject findNearestEnemyBase(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		AbstractActionableObject nearestBase = null;
		
		
		for (Base base : space.getBases()) {
			if(!base.getTeamName().equalsIgnoreCase(ship.getTeamName())){
				//targets supplimentary bases first, why should they get more than one?
				if(!base.isHomeBase()){
					double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
					if(dist < minDistance) {
						minDistance = dist;
						nearestBase = base;
					}
				}
				//only will target home bases if they have energy to kill
				else if (base.isHomeBase() && base.getEnergy() > 500) {
					double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
					if (dist < minDistance) {
						minDistance = dist;
						nearestBase = base;
					}
				}
				else{
					nearestBase = nearestEnemy(space,ship);
				}
			}
		}
		return nearestBase;
	}
	
	/**
	 * Finds the nearest enemy, because shooting at just bases can get boring.
	 * 
	 * @param space
	 * @param ship
	 * @return
	 * 
	 */
	public static Ship nearestEnemy(Toroidal2DPhysics space, Ship ship){
		double nearest = Double.MAX_VALUE;
		Ship nearShip = null;
		for(Ship notUs : space.getShips()){
			if(!notUs.getTeamName().equalsIgnoreCase(ship.getTeamName())){
				double distance = space.findShortestDistance(ship.getPosition(), notUs.getPosition());
				if(distance < nearest){
					nearest = distance;
					nearShip = notUs;
				}
			}
		}
		return nearShip;
	}
	
	/**
	 * Use if your ship needs to eat
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	public static AbstractObject nearestBeacon(Toroidal2DPhysics space, Ship ship){
		double nearest = Double.MAX_VALUE;
		AbstractObject energy = null;
		for(Beacon power : space.getBeacons()){
			double distance = space.findShortestDistance(ship.getPosition(), power.getPosition());
			if(distance < nearest){
				nearest = distance;
				energy = power;
			}
		}
		return energy;
	}
	
	/**
	 * checks the base for an enemy
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	public static AbstractActionableObject getEnemyNearBase(Toroidal2DPhysics space, Ship ship){
		double withinDistance = 150;
		for(Ship notUs : space.getShips()){
			for(Base us : space.getBases()){
				if(!notUs.getTeamName().equalsIgnoreCase(ship.getTeamName()) && us.getTeamName().equalsIgnoreCase(ship.getTeamName())){
					if(notUs.getPosition().getX() < (us.getPosition().getX() + withinDistance) && 
							notUs.getPosition().getX() > (us.getPosition().getX() - withinDistance)){
						if(notUs.getPosition().getY() < (us.getPosition().getY() + withinDistance) &&
								notUs.getPosition().getY() > (us.getPosition().getY() - withinDistance)){
							return notUs;
						}
					}
				}
			}
		}
		return null;
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
		Vector2D direction = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());

		//speed adjustments relative to max accel
		double movementFactor = 2.25;
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
					(Functions.willHitMovingTarget(space,ship,target,target.getPosition().getTranslationalVelocity()) ||
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
	 * Attempt at a ship brake with turn and burn mechanics
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	public static AbstractAction brake(Toroidal2DPhysics space, Ship ship, AbstractObject target){
		AbstractAction stop = null;
		if(!isAimingAtTarget(space,ship,target))
			if(ship.getPosition().getTranslationalVelocity().getMagnitude() > 10.0){
				Vector2D currentPath = ship.getPosition().getTranslationalVelocity();
				stop = new MoveAction(space,ship.getPosition(),ship.getPosition(),currentPath.negate());
				stop = nextVector(space,ship,target, Movement.MAX_TRANSLATIONAL_ACCELERATION*2);
			}
			else{
				
				double angles = Movement.MAX_ANGULAR_ACCELERATION*.2;
				if(space.findShortestDistanceVector(ship.getPosition(), target.getPosition()).getAngle() > Math.PI){
					//stop = new RawAction(0,1);
					stop = nextVector(space,ship,target, Movement.MAX_TRANSLATIONAL_ACCELERATION*3);
				}
				else if(space.findShortestDistanceVector(ship.getPosition(), target.getPosition()).getAngle() < Math.PI){
					//stop = new RawAction(0,1);
					stop = nextVector(space,ship,target, Movement.MAX_TRANSLATIONAL_ACCELERATION*3);
				}
				
				RawAction go = new RawAction(0,0);
				
			}
		else
			stop = advancedMovementVector(space,ship,target,10);
		return stop;
	}
	
	
	/**
	 * See if a ship is pointed at at target.
	 * If this returns true, your shot is guaranteed to hit a stationary target, as long as there's nothing in between them. 
	 * Only wraps torus once
	 * @param ship
	 * @param target
	 * @return
	 */
	public static boolean isAimingAtTarget(Toroidal2DPhysics space, Ship ship, AbstractObject target){
		double shipX = ship.getPosition().getX();
		double shipY = ship.getPosition().getY();
		
		//Position's orientation is organized as follows: 
		//	Top is negative, going from -Pi (left side) to 0 (right side).
		//	Bottom is positive, also going from Pi (left side) to 0 (right side)
		//	Units are radians
		double shipOrientation = ship.getPosition().getOrientation();
		
		//Using the distance function here: http://math.stackexchange.com/questions/275529/check-if-line-intersects-with-circles-perimeter
		double a = Math.tan(shipOrientation);
		double b = -1;
		double c = shipY - a * shipX;
		
		double targetX = target.getPosition().getX();
		double targetY = target.getPosition().getY();
		
		//Adjust for toroidal math
		//Ship is facing down & target is above ship, move target down a screen
		if(shipOrientation > 0.0 && targetY < shipY){
			targetY += space.getHeight();
		}
		//Ship is facing up & target is below ship, move target up a screen
		if(shipOrientation < 0.0 && targetY > shipY){
			targetY -= space.getHeight();
		}
		//Ship is facing right & target is to the left, move target right a screen
		if(Math.abs(shipOrientation) < Math.PI / 2 && targetX < shipX){
			targetX += space.getWidth();
		}
		//Ship is facing left & target is to the right of ship, move target left a screen
		if(Math.abs(shipOrientation) > Math.PI / 2 && targetX > shipX){
			targetX -= space.getWidth();
		}
		
		//Distance from line to center of object
		//Using the distance function here: http://math.stackexchange.com/questions/275529/check-if-line-intersects-with-circles-perimeter
		double distanceToTargetCenter = Math.abs(a * targetX + b * targetY + c) / Math.sqrt(a*a + b*b);
		
		if(distanceToTargetCenter <= target.getRadius() + Missile.MISSILE_RADIUS - 1){
			return true;
		}
		else{
			return false;
		}
	}
	
	/**
	 * See if shooting now will let us hit a moving target, considering possible obstructions
	 * Assumes no change in velocity for any objects, which is probably not true
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	public static boolean willMakeItToTarget(Toroidal2DPhysics space, Ship ship, AbstractObject target, Vector2D targetEstimatedVelocity){		
		Missile fakeMissile = new Missile(ship.getPosition(), ship);
		
		double timeUntilHitTarget = timeUntilCollision(space, 
													fakeMissile.getPosition(), fakeMissile.getRadius(), fakeMissile.getPosition().getTranslationalVelocity(),
													target.getPosition(), target.getRadius(), targetEstimatedVelocity);
		
		//If it won't hit the target in the first place
		if(timeUntilHitTarget < 0){
			return false;
		}
		
		for(AbstractObject obstruction : space.getAllObjects()){
			//Don't worry about objects that bullets will move through
			if(obstruction instanceof Beacon){
				continue;
			}
			//Don't worry about hitting our target
			if(obstruction.getId() == target.getId()){
				continue;
			}
			
			double timeUntilCollision = timeUntilCollision(space,
															fakeMissile.getPosition(), fakeMissile.getRadius(), fakeMissile.getPosition().getTranslationalVelocity(),
															obstruction.getPosition(), obstruction.getRadius(), obstruction.getPosition().getTranslationalVelocity());
			
			if(timeUntilCollision >= 0 && timeUntilCollision < timeUntilHitTarget){
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * See if shooting now will let us hit a moving target, assuming no obstructions
	 * @param space
	 * @param ship
	 * @param target
	 * @param targetEstimatedVelocity
	 * @return
	 */
	public static boolean willHitMovingTarget(Toroidal2DPhysics space, Ship ship, AbstractObject target, Vector2D targetEstimatedVelocity){
		Missile fakeMissile = new Missile(ship.getPosition(), ship);
		
		double timeUntilCollision = timeUntilCollision(space, 
														fakeMissile.getPosition(), Missile.MISSILE_RADIUS, fakeMissile.getPosition().getTranslationalVelocity(),
														target.getPosition(), target.getRadius(), targetEstimatedVelocity);
		
		if(timeUntilCollision >= 0){
			return true;
		}
		return false;
	}
	
	/**
	 * See when two objects will collide. 
	 * Will usually be off by a tiny bit, since it treats objects as boxes, not squares, but will occasionally have a false positive for collisions.
	 * @return Estimated time until first collision, or -1 if there shouldn't be one.
	 */
	public static double timeUntilCollision(Toroidal2DPhysics space,
											Position firstObjectPosition, double firstObjectRadius, Vector2D firstEstimatedVelocity,
											Position secondObjectPosition, double secondObjectRadius, Vector2D secondEstimatedVelocity){
		//TODO: Adapt this other approach to a toroidal surface- should be more accurate.
		//http://gamedev.stackexchange.com/questions/97337/detect-if-two-objects-are-going-to-crash
		
		//Basic idea: see when the objects will occupy the same area on the X and Y axes. If they occupy the same space on both axes at the same time, they're colliding
		
		double[][] xCollisions = intersectingTimespan(firstObjectPosition.getX(), firstObjectRadius, firstEstimatedVelocity.getXValue(),
														secondObjectPosition.getX(), secondObjectRadius, secondEstimatedVelocity.getXValue(),
														space.getWidth());
		double[][] yCollisions = intersectingTimespan(firstObjectPosition.getY(), firstObjectRadius, firstEstimatedVelocity.getYValue(),
														secondObjectPosition.getY(), secondObjectRadius, secondEstimatedVelocity.getYValue(),
														space.getHeight());
		
		//Finding the least amount of time the objects can occupy the same x and y values before we classify it as a collision
		//It depends on the relative speed of the missile & the size of the target
		//Remember, this approach treats objects as squares, not circles, so this lessens the impact of that
		Vector2D relativeVelocity = firstEstimatedVelocity.add(secondEstimatedVelocity);
		double relativeSpeed = relativeVelocity.getMagnitude(); 
		double maxTimeColliding = (firstObjectRadius + secondObjectRadius) / relativeSpeed;
		
		double pickinessFactor = 1; //Found by experimentation to work well. The higher this is, the fewer collisions will be found (including both real & fake collisions). 
 		double timeSpentCollidingThreshold = maxTimeColliding / pickinessFactor;

		double smallestTimeUntilCollision = Double.MAX_VALUE;
		for(double[] xPeriod : xCollisions){
			for(double[] yPeriod : yCollisions){
				
				//If the boxes collide
				if( xPeriod[0] < yPeriod[1] && xPeriod[1] > yPeriod[0] ){
					double timeSpentColliding = Math.min(xPeriod[1] - yPeriod[0], yPeriod[1] - xPeriod[0]);
					
					if (timeSpentColliding > timeSpentCollidingThreshold) {
						double timeUntilCollision = Math.max(xPeriod[0], yPeriod[0]);
						if (timeUntilCollision < smallestTimeUntilCollision) {
							smallestTimeUntilCollision = timeUntilCollision;
						}
					}
				}
				
			}
		}
		
		if (smallestTimeUntilCollision == Double.MAX_VALUE){
			smallestTimeUntilCollision = -1;
		}
		
		return smallestTimeUntilCollision;
	}

	/**
	 * See when two objects will collide. 
	 * Will usually be off by a tiny bit, since it treats objects as boxes, not squares, but will occasionally have a false positive for collisions.
	 * @return Estimated time until first collision, or -1 if there shouldn't be one.
	 */
	public static double timeUntilCollision(Toroidal2DPhysics space, 
											AbstractObject firstObject, Vector2D firstEstimatedVelocity, 
											AbstractObject secondObject, Vector2D secondEstimatedVelocity){
		return timeUntilCollision(space, 
									firstObject.getPosition(), firstObject.getRadius(), firstEstimatedVelocity,
									secondObject.getPosition(), secondObject.getRadius(), secondEstimatedVelocity);
	}

	/**
	 * See when two objects will collide. 
	 * Will usually be off by a tiny bit, since it treats objects as boxes, not squares, but will occasionally have a false positive for collisions.
	 * @return Estimated time until first collision, or -1 if there isn't be one.
	 */
	public static double timeUntilCollision(Toroidal2DPhysics space, AbstractObject firstObject, AbstractObject secondObject){
		return timeUntilCollision(space, 
									firstObject, firstObject.getPosition().getTranslationalVelocity(),
									secondObject, secondObject.getPosition().getTranslationalVelocity());
	}
											
	
	/**
	 * Get the first two periods during which two objects are occupying the same space on a single axis
	 * @param firstPosition
	 * @param firstRadius
	 * @param firstVelocity
	 * @param secondPosition
	 * @param secondRadius
	 * @param secondVelocity
	 * @param spaceWidth
	 * @return
	 */
	private static double[][] intersectingTimespan(double centerA, double radiusA, double velocityA, 
													double centerB, double radiusB, double velocityB,
													int spaceWidth){
		double[][] timespan = {{0, 0},{0,0}};
		
		boolean alreadyCollided = Math.abs(centerA - centerB) < (radiusA + radiusB);
		
		if(velocityA == velocityB){
			if(alreadyCollided){
				timespan[0][1] = Double.MAX_VALUE;
			}
			return timespan;
		}
		
		//Fastest here refers to most positive velocity
		//So, 5 is considered faster than -10
		boolean aIsFaster = velocityA > velocityB;
		
		double fastestBoxRightEdge = aIsFaster ? centerA + radiusA : centerB + radiusB;
		double fastestBoxLeftEdge  = aIsFaster ? centerA - radiusA : centerB - radiusB;
		double slowestBoxRightEdge = aIsFaster ? centerB + radiusB : centerA + radiusA;
		double slowestBoxLeftEdge  = aIsFaster ? centerB - radiusB : centerA - radiusA;
		

		//If the velocity of A > B (*not the magnitude!*), 
		//  then the collision will always involve A's right side hitting B's left side
		double distanceBetweenInnerEdges = distanceBetweenEdges(fastestBoxRightEdge, slowestBoxLeftEdge, spaceWidth);
		double distanceBetweenOuterEdges = distanceBetweenEdges(fastestBoxLeftEdge, slowestBoxRightEdge, spaceWidth);
		
		double effectiveSpeed = Math.abs(velocityA - velocityB);
		double timeToCollide = distanceBetweenInnerEdges / effectiveSpeed;
		double timeToStopColliding = distanceBetweenOuterEdges / effectiveSpeed;
		
		if(alreadyCollided){
			//If we've already collided, then timeToStopColliding will be smaller than timeToCollide
			timespan[0][0] = 0;
			timespan[0][1] = timeToStopColliding;
			timespan[1][0] = timeToCollide;
			timespan[1][1] = timeToCollide + ((radiusA + radiusB) / spaceWidth);
		}
		else{
			timespan[0][0] = timeToCollide;
			timespan[0][1] = timeToStopColliding;
			timespan[1][0] = timeToCollide + (spaceWidth / effectiveSpeed);
			timespan[1][1] = timeToStopColliding + (spaceWidth / effectiveSpeed);
		}
		
		return timespan;
	}
	
	private static double distanceBetweenEdges(double fasterEdge, double slowerEdge, double spaceWidth){
		double distance = slowerEdge - fasterEdge;
		//If the faster one is to the right of the slower one, then it'll wrap through the wall
		if(fasterEdge > slowerEdge){
			distance = spaceWidth - distance; 
		}
		return distance;
	}
	
	
}
