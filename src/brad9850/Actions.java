package brad9850;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;



/**
 * 
 * @author Scott Kannawin & Christopher Bradford
 * 
 * Methods used to grab the teamwork actions
 *
 */
public class Actions {
	public static AbstractObject getActions(Toroidal2DPhysics space, ArrayList<UUID> ships, UUID ship, ArrayList<Asteroid> bestAsteroids, ArrayList<AbstractObject> otherTargets){
		AbstractObject actionList = space.getObjectById(ship);
		Ship currentShip = (Ship) space.getObjectById(ship);
		switch(ships.indexOf(ship)){
			case 0: //flag bearer #1
				actionList = flagBearer(space,currentShip);
				break;	
			case 1: //flag bearer #2
				actionList = flagBearer(space,currentShip);
				break;
			case 2: //resource gatherer #1
				actionList = gatherer(space,currentShip,bestAsteroids,otherTargets);
				break;
			case 3: //resource gatherer #2
				actionList = gatherer(space,currentShip,bestAsteroids,otherTargets);
				break;
			case 4: //defender
				actionList = defender(space,currentShip,bestAsteroids,otherTargets);
				break;
			case 5: //harasser
				actionList = Combat.nearestEnemy(space, currentShip);
				break;
		}
	
		return actionList;
	}
	
	//Figures the location for the flag, or return base of the ship if it has the flag
	public static AbstractObject flagBearer(Toroidal2DPhysics space, Ship ship){
		AbstractObject movementGoal = null;
		
		double baseDistance = Double.MAX_VALUE;
		
		//Go to either the flag or the base
		if(ship.isCarryingFlag()){
			//If we're carrying the flag, return to base
			for(Base base : space.getBases()){
				if(base.getTeamName().equalsIgnoreCase(ship.getTeamName())
						&& space.findShortestDistance(base.getPosition(), ship.getPosition()) < baseDistance){
					baseDistance = space.findShortestDistance(base.getPosition(), ship.getPosition());
					movementGoal = base;
				}
			}
		}	
		else{
			//If we're not carrying the flag, hunt it down
			for(Flag flag : space.getFlags()){
				if(!flag.getTeamName().equalsIgnoreCase(ship.getTeamName()) && !flag.isBeingCarried()){
					movementGoal = flag;
				}
			}
		}
		//If we aren't going anywhere yet, it means someone else is carrying the flag
		//In that case, hang out nearby the flag alcoves
		if(movementGoal == null){
			double enemyMainX = 0;
			double allyMainX = 0;
			double mainY = 0;
			for(Base base : space.getBases()){
				if(base.isHomeBase()){
					mainY = base.getPosition().getY();
					if(base.getTeamName().equalsIgnoreCase(ship.getTeamName())){
						allyMainX = base.getPosition().getX();
					}
					else{
						enemyMainX = base.getPosition().getX();
					}
				}
				Position fakeAsteroidPosition = new Position(enemyMainX + (enemyMainX - allyMainX) * 3, mainY);
				movementGoal = new Asteroid(fakeAsteroidPosition, false, ship.getRadius(), false, 0, 0, 0);
			}
			
		}
		return movementGoal;
	}
	
	//will return the shortest target to the ship on our half of the map, prioritizing ships carrying flags
	//all defenders can attack the same target as there will be no conflicts
	public static AbstractObject defender(Toroidal2DPhysics space, Ship ship, ArrayList<Asteroid> bestAsteroids, ArrayList<AbstractObject> otherTargets){
		int halfMin = 0;
		int halfMax = space.getWidth() / 2;
		AbstractObject target = null;
		double shortestDistance = Double.MAX_VALUE;
		
		//need to find which half of the map our base is on
		for(Base base : space.getBases()){
			if(base.getTeamName().equalsIgnoreCase(ship.getTeamName())){
				if(base.getPosition().getX() > halfMax && base.isHomeBase()){
					halfMin = space.getWidth() / 2;
					halfMax = space.getWidth();
				}
			}
		}
		
		//collect possible targets
		ArrayList<AbstractObject> targetList = new ArrayList<AbstractObject>();
		//get bases on our half
		for(Base enemy : space.getBases()){
			if(enemy.getTeamName().equalsIgnoreCase(ship.getTeamName()) == false
					&& enemy.getPosition().getX() > halfMin
					&& enemy.getPosition().getX() < halfMax){
				targetList.add(enemy);
			}
		}
		//get ships on our half
		for(Ship enemy : space.getShips()){
			if(enemy.getTeamName().equalsIgnoreCase(ship.getTeamName()) == false){
				//If the ship is carrying our flag, always focus it first
				if(enemy.isCarryingFlag()){
					target = enemy;
				}
				if (enemy.getPosition().getX() > halfMin && enemy.getPosition().getX() < halfMax) {
					targetList.add(enemy);
				}
			}
		}
		//If there's no flag carrier, hunt the closest enemy
		if (target == null) {
			for (AbstractObject enemy : targetList) {
				if (space.findShortestDistance(enemy.getPosition(), ship.getPosition()) < shortestDistance) {
					shortestDistance = space.findShortestDistance(enemy.getPosition(), ship.getPosition());
					target = enemy;
				}
			}
		}
		//if there is nothing on our half, go for a resource
		if(target == null){
			target = gatherer(space, ship, bestAsteroids, otherTargets);
		}
			
		return target;
	}
	
	//determines for a gatherer ship to get a resource closest to the base
	public static AbstractObject gatherer(Toroidal2DPhysics space, Ship ship, ArrayList<Asteroid> bestAsteroids, ArrayList<AbstractObject> otherTarget){
		AbstractObject movementGoal = null;
		double shortDistance = Double.MAX_VALUE;
		//get the base closest to the ship
		for(Base base : space.getBases()){
			if(base.getTeamName().equalsIgnoreCase(ship.getTeamName())
					&& space.findShortestDistance(base.getPosition(), ship.getPosition()) < shortDistance){
				shortDistance = space.findShortestDistance(base.getPosition(), ship.getPosition());
				movementGoal = base;
			}
		}

		//if total number of resources is less than 1000, go for a mineable asteroid
		if (ship.getResources().getTotal() < 1000) {
			movementGoal = findClosestFreeAsteroid(space, ship, bestAsteroids, otherTarget);
			if (movementGoal == null) {
				movementGoal = findClosestFreeAsteroid(space, ship, space.getAsteroids(), otherTarget);
			}
		}
		
		return movementGoal;
	}
	
	private static Asteroid findClosestFreeAsteroid(Toroidal2DPhysics space, Ship ship, Collection<Asteroid> asteroids, ArrayList<AbstractObject> otherTarget){
		Asteroid movementGoal = null;
		double shortDistance = Double.MAX_VALUE;
		for (Asteroid asteroid : asteroids) {
			if (asteroid.isMineable()
					&& space.findShortestDistance(asteroid.getPosition(), ship.getPosition()) < shortDistance) {
				boolean skip = false;
				for (int i = 0; i < otherTarget.size(); i++) {
					if (asteroid.getPosition() == otherTarget.get(i).getPosition()) {
						skip = true;
						break;
					}
				}
				if (!skip) {
					shortDistance = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());
					movementGoal = asteroid;
				}
			}
		}
		return movementGoal;
	}
}


