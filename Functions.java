package brad9850;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.RawAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Vector2D;

public class Functions{
	
	public Functions(){
		
	}
	
	/**
	 * See if a team is a non-AI enemy
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
	public AbstractObject findNearestEnemyBase(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		AbstractObject nearestBase = null;
		
		
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
				else if (base.isHomeBase() && base.getHealingEnergy() > 250) {
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
	public Ship nearestEnemy(Toroidal2DPhysics space, Ship ship){
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
	public AbstractObject nearestBeacon(Toroidal2DPhysics space, Ship ship){
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
	public AbstractObject isEnemyNearBase(Toroidal2DPhysics space, Ship ship){
		for(Ship notUs : space.getShips()){
			for(Base us : space.getBases()){
				if(!notUs.getTeamName().equalsIgnoreCase(ship.getTeamName()) && us.getTeamName().equalsIgnoreCase(ship.getTeamName())){
					if(notUs.getPosition().getX() < (us.getPosition().getX() + 150) && 
							notUs.getPosition().getX() > (us.getPosition().getX() - 150)){
						if(notUs.getPosition().getY() < (us.getPosition().getY() + 150) &&
								notUs.getPosition().getY() > (us.getPosition().getY() - 150)){
							return notUs;
						}
					}
				}
			}
		}
		return null;
	}
	
	 
	
	
	/**
	 * Basic movement vector
	 * Goes to the next location quickly, currently max velocity is at 4*max_accel
	 * Because it gets out of control, it's toned down to 3*max_accel
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	public AbstractAction nextVector(Toroidal2DPhysics space, Ship ship, AbstractObject target){
		//get the direction it is going
		Vector2D direction = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());
		Vector2D gotoPlace = new Vector2D();
		//use that angle for which it is going to accelerate, and set the magnitude up
		gotoPlace = gotoPlace.fromAngle(direction.getAngle(),Movement.MAX_TRANSLATIONAL_ACCELERATION*3);
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
	public AbstractAction advancedMovementVector(Toroidal2DPhysics space, Ship ship, AbstractObject target, int distanceFactor){
		Vector2D direction = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());
		Vector2D gotoPlace = new Vector2D(ship.getPosition());
		gotoPlace = gotoPlace.fromAngle(gotoPlace.angleBetween(direction),Movement.MAX_TRANSLATIONAL_ACCELERATION*3);
		
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
			if(isAimingAtTarget(space,ship,target) && target.getClass() == Beacon.class)
				sendOff = new MoveAction(space,ship.getPosition(),target.getPosition(),new Vector2D(ship.getPosition()).fromAngle(direction.getAngle(), Movement.MAX_TRANSLATIONAL_ACCELERATION*3));
			else{
				sendOff = new MoveToObjectAction(space, ship.getPosition(), target);
				sendOff.getMovement(space, ship).setAngularAccleration(Movement.MAX_ANGULAR_ACCELERATION);
			}
		}
		//if path is clear it will go
		else if(space.isPathClearOfObstructions(ship.getPosition(), target.getPosition(), asteroids, 0))
			sendOff = nextVector(space,ship,target);
		
		//else it will find a new target
		else{
			AbstractObject nextLocation = nextFreeVector(space,ship,target);
			Vector2D vectorTarget = new Vector2D(ship.getPosition());
			vectorTarget.fromAngle(vectorTarget.angleBetween(new Vector2D(target.getPosition())), Movement.MAX_TRANSLATIONAL_ACCELERATION*3);
			//sendOff = new MoveAction(space,ship.getPosition(),nextFreeVector(space,ship,target).getPosition(),vectorTarget);
			sendOff = nextVector(space,ship,nextFreeVector(space,ship,target));
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
	private AbstractObject nextFreeVector(Toroidal2DPhysics space, Ship ship, AbstractObject target){
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
		
		//finds the shortest free path
		for(AbstractObject obj : targetObjs){
			double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());
			if(distance < minDistance && space.isPathClearOfObstructions(ship.getPosition(), obj.getPosition(), nonShootable, 0)){
				minDistance = distance;
				gotoTarget = obj;
			}
		}
		
		
		return gotoTarget;
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
		if(shipOrientation > 0 && targetY < shipY){
			targetY += space.getHeight();
		}
		//Ship is facing up & target is below ship, move target up a screen
		if(shipOrientation < 0 && targetY > shipY){
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
		
		double distanceToTargetCenter = Math.abs(a * targetX + b * targetY + c) / Math.sqrt(a*a + b*b);
		
		if(distanceToTargetCenter <= target.getRadius() + Missile.MISSILE_RADIUS){
			return true;
		}
		else{
			return false;
		}
	} 
	
	
}