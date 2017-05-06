package brad9850;

import java.util.ArrayList;
import java.util.UUID;

import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.Team;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * A class for handling the a simplified state of the world during planning 
 * @author chris
 *
 */
public class PlanState{
	//Whether or not a ship has been bought
	boolean[] shipBought;
	//The ship ID
	ArrayList<UUID> shipID;
	//How long a ship is busy with its planned tasks
	int[] shipOccupiedUntil;
	//How many resources the ship is carrying
	ResourcePile[] shipCarrying;
	//The ship that is waiting to deposit a flag
	int shipOnStandby;
	//The ship that is carrying the flag
	int shipCarryingFlag;
	
	//The asteroid's ID
	ArrayList<UUID> asteroidID;
	//Which ship has claimed a certain asteroid 
	int[] asteroidClaimedBy;

	//The number of non-home bases built
	int baseCount;
	//The number of flags the team has
	int flagCount;
	//How much buying various objects costs
	ResourcePile shipCost;
	ResourcePile baseCost;
	//The total amount of resources collected
	ResourcePile totalResources;
	//The total amount of time taken so far
	int totalDuration;
	//Estimated times to accomplish tasks
	int estimatedTimeToBase;
	int estimatedTimeToFlag;
	int estimatedTimeToAsteroid;
	
	final int maxShipCount = 6;
	final int maxAsteroidCount = 10;
	
	/**
	 * Initialize the state based on the current world
	 * @param space
	 * @param teamName
	 */
	public PlanState(Toroidal2DPhysics space, String teamName){
		//Initialize everything
		initShips(space, teamName);
		initAsteroids(space);
		
		baseCount = 0;
		totalDuration = 0;
		
		//Find the team object
		Team team = null;
		//For some mysterious reason, this is the only way to do so
		for(Base base : space.getBases()){
			if(base.getTeamName().equalsIgnoreCase(teamName)){
				team = base.getTeam();
			}
		}
		
		totalResources = team.getAvailableResources();
		flagCount = (int) team.getScore();
		shipCost = team.getCurrentCost(PurchaseTypes.SHIP);
		baseCost = team.getCurrentCost(PurchaseTypes.BASE);
		
		estimatedTimeToBase = 500;
		estimatedTimeToFlag = 500;
		estimatedTimeToAsteroid = 500;
	}
	
	/**
	 * Make a deep copy of another state
	 * @param otherState
	 */
	public PlanState(PlanState otherState){
		shipBought = otherState.shipBought.clone();
		shipID = new ArrayList<UUID>(otherState.shipID);
		shipOccupiedUntil = otherState.shipOccupiedUntil.clone();
		shipCarrying = new ResourcePile[otherState.shipCarrying.length];
		for(int i = 0; i < shipCarrying.length; i++){
			if (otherState.shipCarrying[i] != null) {
				shipCarrying[i] = new ResourcePile(otherState.shipCarrying[i]);
			}
			else{
				shipCarrying[i] = new ResourcePile();
			}
		}
		shipOnStandby = otherState.shipOnStandby;
		shipCarryingFlag = otherState.shipCarryingFlag;

		asteroidID = new ArrayList<UUID>(otherState.asteroidID);
		asteroidClaimedBy = otherState.asteroidClaimedBy.clone();

		baseCount = otherState.baseCount;
		flagCount = otherState.flagCount;
		totalResources = new ResourcePile(otherState.totalResources);
		totalDuration = otherState.totalDuration;
		shipCost = new ResourcePile(otherState.shipCost);
		baseCost = new ResourcePile(otherState.baseCost);
		estimatedTimeToBase = otherState.estimatedTimeToBase;
		estimatedTimeToFlag = otherState.estimatedTimeToFlag;
		estimatedTimeToAsteroid = otherState.estimatedTimeToAsteroid;
	}
	
	/**
	 * Initialize the info related to ships
	 * @param space
	 * @param teamName
	 */
	private void initShips(Toroidal2DPhysics space, String teamName){
		shipBought = new boolean[maxShipCount];
		shipID = new ArrayList<UUID>();
		shipOccupiedUntil = new int[maxShipCount];
		shipCarrying = new ResourcePile[maxShipCount];
		
		shipOnStandby = -1;
		shipCarryingFlag = -1;
		
		int shipIndex = 0;
		for(Ship ship : space.getShips()){
			if(ship.getTeamName().equalsIgnoreCase(teamName)){
				if(ship.isCarryingFlag()){
					shipCarryingFlag = shipIndex;
				}
				shipBought[shipIndex] = true;
				shipID.add(ship.getId());
				shipCarrying[shipIndex] = ship.getResources();
				shipIndex++;
			}
		}
	}
	
	/**
	 * Initialize the info related to asteroids
	 * @param space
	 * @param teamName
	 */
	private void initAsteroids(Toroidal2DPhysics space){
		asteroidID = new ArrayList<UUID>();
		asteroidClaimedBy = new int[maxAsteroidCount];
		
		int asteroidIndex = 0;
		for(Asteroid asteroid : space.getAsteroids()){
			if(asteroid.isMineable()){
				asteroidID.add(asteroid.getId());
				asteroidClaimedBy[asteroidIndex] = -1;
				asteroidIndex += 1;
			}
		}
	}
	
}
