package brad9850;

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
 * Reflex-based Agent that prioritizes defending its base, destroying other bases, and hunting down ships, in that order
 * @author Christopher Bradford & Scott Kannawin
 */
public class ChaseBot extends TeamClient {
	boolean shouldShoot = false;
	boolean boost = false;
	ArrayList<UUID> nextPosition = new ArrayList<UUID>();
	int lastTimestep = 0;

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
		
		AbstractAction newAction = null;
		
		//if the next target is dead, it has been 150 timesteps since last refresh, or the list for the path is empty refresh
		if((nextPosition.size() < 1 || !space.getObjectById(nextPosition.get(nextPosition.size() - 1)).isAlive())
				|| (space.getCurrentTimestep() - this.lastTimestep) > 150){
			if(nextPosition.size() > 0){
				if(!space.getObjectById(nextPosition.get(nextPosition.size() - 1)).isAlive()){
					this.nextPosition = Vectoring.movementMap(space, Combat.nearestEnemy(space, ship), ship);
					//get rid of moving to itself first
					this.nextPosition.remove(0);
				}
			}
			else{
				this.nextPosition = Vectoring.movementMap(space, Combat.nearestEnemy(space,ship), ship);
				this.nextPosition.remove(0);
			}
		}
		
		
		//Don't want to shoot beacons when searching for them
		shouldShoot = false;
		if(ship.getEnergy() > 1750){
			if(space.getObjectById(nextPosition.get(0)).isAlive()){
				if(Combat.willHitMovingTarget(space, ship, space.getObjectById(nextPosition.get(0)),
						space.getObjectById(nextPosition.get(0)).getPosition().getTranslationalVelocity())){
					shouldShoot = true;
				}
				if(space.findShortestDistance(ship.getPosition(), space.getObjectById(nextPosition.get(0)).getPosition()) < 50){
					newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
					nextPosition.remove(0);
				}
				else{
					newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
				}
			}
			else{
				this.nextPosition = Vectoring.movementMap(space, Combat.nearestBeacon(space, ship), ship);
				newAction = Vectoring.advancedMovementVector(space, ship, space.getObjectById(nextPosition.get(0)), 150);
			}			
		}
		else{
			newAction = Vectoring.advancedMovementVector(space, ship, Combat.nearestBeacon(space, ship), 150);
		}
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