package brad9850;
package kann0200;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Position;
/**
 * A* based Agent that only hunts down the nearest enemy
 * It traverses using distance between nodes (mineable asteroids, and beacons)
 * The heuristic function is direct distance to the target
 * It gets a path by seeing if between asteroid is a non mineable asteroid and deletes that edge
 * It gets the shortest path using the Floyd-Warshall all pairs shortest path algorithm
 * 
 * @author Christopher Bradford & Scott Kannawin
 */
public class ChaseBot extends TeamClient {
	boolean shouldShoot = false;
	boolean boost = false;
	ArrayList<UUID> nextPosition = new ArrayList<UUID>();
	int lastTimestep = 0;
	UUID currentTarget = null;

	/**
	 * 
	 */
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();

		// loop through each ship
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				AbstractAction action = getAction(space, ship);
				actions.put(ship.getId(), action);
				
			} else {
				// it is a base.  Heuristically decide when to use the shield (TODO)
				actions.put(actionable.getId(), new DoNothingAction());
			}
		} 
		return actions;
	}
	
	/**
	 * Gets the action for our ship
	 * @param space
	 * @param ship
	 * @return
	 */
	private AbstractAction getAction(Toroidal2DPhysics space, Ship ship) {
		//ship.getPosition().setAngularVelocity(Movement.MAX_ANGULAR_ACCELERATION);
		//nullify from previous action
		ship.setCurrentAction(null);
		
		AbstractAction newAction = new DoNothingAction();
		
		//if the next target is dead, it has been 150 timesteps since last refresh, or the list for the path is empty refresh
		if(nextPosition.size() < 1
				|| (space.getCurrentTimestep() - this.lastTimestep) > 15 
				|| space.getObjectById(this.currentTarget) == null
				|| !space.getObjectById(this.currentTarget).isAlive())
		{	
				this.nextPosition = new ArrayList<UUID>();
				this.lastTimestep = space.getCurrentTimestep();
				this.nextPosition = Vectoring.movementMap(space, Combat.nearestEnemy(space,ship), ship);
				this.nextPosition.remove(0);
				this.currentTarget = space.getObjectById(this.nextPosition.get(this.nextPosition.size() - 1)).getId();
		}
		
		
		if(Combat.willHitMovingTarget(space, ship, space.getObjectById(this.currentTarget), space.getObjectById(this.currentTarget).getPosition().getTranslationalVelocity())){
			shouldShoot= true;
		}
		else{
			shouldShoot = false;
		}
		
		//if(ship.getEnergy() > 1750){
		if(space.getObjectById(this.currentTarget).isAlive()){

			
			//TODO fix the logic here, should be if it is within a certain distance of the next target move to next item
			//else if target is still alive be on it
			//account for if an item is null or not (picked up / destroyed)
			if(!space.getObjectById(this.currentTarget).isAlive() || space.getObjectById(this.nextPosition.get(0)) == null)
			{
				if(nextPosition.size() > 1){
					nextPosition.remove(0);
					//newAction = new MoveToObjectAction(space, ship.getPosition(), space.getObjectById(nextPosition.get(0)));
					newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
				}
				else{
					this.nextPosition = new ArrayList<UUID>();
					this.nextPosition = Vectoring.movementMap(space, Combat.nearestEnemy(space, ship), ship);
					this.nextPosition.remove(0);
					newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
				}
			}
			else{
				//newAction = new MoveToObjectAction(space, ship.getPosition(), space.getObjectById(nextPosition.get(0)));
				newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
			}
		}
		else{
			this.nextPosition = new ArrayList<UUID>();
			this.nextPosition = Vectoring.movementMap(space, Combat.nearestEnemy(space, ship), ship);
			this.nextPosition.remove(0);
			newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
		}
			/*
		}
		else{
			newAction = Vectoring.advancedMovementVector(space, ship, Combat.nearestBeacon(space, ship), 150);
		}
		*/
		return newAction;
	}
	
	
	

	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
	}

	@Override
	public void initialize(Toroidal2DPhysics space) {
	}

	@Override
	public void shutDown(Toroidal2DPhysics space) {
		// TODO Auto-generated method stub

	}

	@Override
	public Set<SpacewarGraphics> getGraphics() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	/**
	 * Never buy anything
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, 
			ResourcePile resourcesAvailable, 
			PurchaseCosts purchaseCosts) {

		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();


		return purchases;
	}

	/**
	 * Shoot whenever we can.
	 * 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, SpaceSettlersPowerupEnum> powerUps = new HashMap<UUID, SpaceSettlersPowerupEnum>();

		for (AbstractActionableObject actionableObject : actionableObjects){
			SpaceSettlersPowerupEnum powerup = SpaceSettlersPowerupEnum.FIRE_MISSILE;
			
			
			//Shoot less often when we're moving fast to prevent our bullets from colliding with each other
			//TODO: Only limit this if we're aiming in the same direction we're traveling
			double vx = actionableObject.getPosition().getxVelocity();
			double vy = actionableObject.getPosition().getyVelocity();
			double shipSpeed = Math.sqrt(vx * vx + vy * vy);
			int shootingDelay = 2 + (int)((shipSpeed - 15)/15);
			
			//If the ship is close to going as fast as a missile, don't shoot
			if(shipSpeed + 10 > Missile.INITIAL_VELOCITY){
				shootingDelay = Integer.MAX_VALUE;
			}
			
			boolean bulletsWontCollide = space.getCurrentTimestep() % shootingDelay == 0;
			
			if (actionableObject.isValidPowerup(powerup) && shouldShoot && bulletsWontCollide){
				powerUps.put(actionableObject.getId(), powerup);
			}
		}
		
		
		return powerUps;
	}

}