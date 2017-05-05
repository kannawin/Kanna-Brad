package brad9850;

import java.util.ArrayList;
import java.util.UUID;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;



/**
 * 
 * @author Scott Kannawin & Christopher Bradford
 * 
 * Methods used to grab the teamwork actions
 *
 */
public class Actions {
	public static AbstractObject getActions(Toroidal2DPhysics space, ArrayList<UUID> ships, UUID ship, ArrayList<AbstractObject> otherTargets){
		AbstractObject actionList = space.getObjectById(ship);
		Ship currentShip = (Ship) space.getObjectById(ship);
		switch(ships.indexOf(ship)){
			case 0: //flag bearer 
				actionList = flagBearer(space,currentShip);
				break;	
			case 1: //defender #1
				actionList = defender(space,currentShip,otherTargets);
				break;
			case 2: //resource gatherer #1
				actionList = gatherer(space,currentShip,otherTargets);
				break;
			case 3: //harasser
				actionList = Combat.nearestEnemy(space, currentShip);
				break;
			case 4: //resource gatherer #2
				actionList = gatherer(space,currentShip,otherTargets);
				break;
			case 5: //defender #2
				actionList = defender(space,currentShip,otherTargets);
				break;
		}
	
		return actionList;
	}
	
	//Figures the location for the flag, or return base of the ship if it has the flag
	public static AbstractObject flagBearer(Toroidal2DPhysics space, Ship ship){
		//Find a default place to move to.
		AbstractObject movementGoal = Combat.nearestBeacon(space, ship);
		
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
				if(!flag.getTeamName().equalsIgnoreCase(ship.getTeamName())){
					movementGoal = flag;
				}
			}
		}
		return movementGoal;
	}
	
	//will return the shortest target to the ship on our half of the map, prioritizing ships carrying flags
	//all defenders can attack the same target as there will be no conflicts
	public static AbstractObject defender(Toroidal2DPhysics space, Ship ship, ArrayList<AbstractObject> otherTargets){
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
			target = gatherer(space,ship,otherTargets);
		}
			
		return target;
	}
	
	//determines for a gatherer ship to get a resource closest to the base
	public static AbstractObject gatherer(Toroidal2DPhysics space, Ship ship, ArrayList<AbstractObject> otherTarget){
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
		
		//if total number of resources is less than 500, go for a mineable asteroid
		if(ship.getResources().getTotal() < 500){
			shortDistance = Double.MAX_VALUE;
			for(Asteroid asteroid : space.getAsteroids()){
				if(asteroid.isMineable() 
						&& space.findShortestDistance(asteroid.getPosition(), ship.getPosition()) < shortDistance){
					boolean skip = false;
					for(int i = 0; i < otherTarget.size(); i++){
						if(asteroid.getPosition() == otherTarget.get(i).getPosition()){
							skip = true;
							break;
						}
					}
					if(!skip){
						shortDistance = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());
						movementGoal = asteroid;
					}
				}
			}
		}
		
		return movementGoal;
	}
}


