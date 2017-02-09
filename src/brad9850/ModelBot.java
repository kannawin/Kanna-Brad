package brad9850;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Position;

/**
 * Model based reflex-based Agent that prioritizes defending its base, destroying other bases, and hunting down ships, in that order
 * @author Christopher Bradford & Scott Kannawin
 * 
 */
public class ModelBot extends TeamClient {
	boolean shouldShoot = false;
	boolean boost = false;
	
	int framesSinceLastShot = 0;
	AbstractActionableObject currentTarget = null;

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
		AbstractAction current = ship.getCurrentAction();
		Position currentPosition = ship.getPosition();
		
		//ship.getPosition().setAngularVelocity(Movement.MAX_ANGULAR_ACCELERATION);
		//nullify from previous action
		ship.setCurrentAction(null);
		
		AbstractAction newAction = null;

		
		//Don't want to shoot beacons when searching for them
		shouldShoot = false;
		if(ship.getEnergy() > 2000){
			//Hunt down a target
			if(isGoodTarget(space, currentTarget)){
				//Use better aiming function if the target is going slow enough
				double vx = currentTarget.getPosition().getxVelocity();
				double vy = currentTarget.getPosition().getyVelocity();
				double speed = Math.sqrt(vx * vx + vy * vy);
				
				if(speed < 5){
					if(Combat.isAimingAtTarget(space, ship, currentTarget)){
						shouldShoot = true;
					}
				}
				else{
					if(Combat.willHitMovingTarget(space, ship, currentTarget, currentTarget.getPosition().getTranslationalVelocity())){
						shouldShoot = true;
					}
				}
				newAction = Vectoring.advancedMovementVector( space, ship, currentTarget, 200);
			}
			else{
				currentTarget = getNextTarget(space, ship);
			}
		}
		else{
			//Look for a beacon
			ship.getPosition().setAngularVelocity(Movement.MAX_ANGULAR_ACCELERATION);
			ship.getPosition().setOrientation(Functions.angleBetween(space, ship, Combat.nearestBeacon(space, ship)));
			newAction = Vectoring.advancedMovementVector(space, ship, Combat.nearestBeacon(space, ship), 150);
		}
		return newAction;
	}
	
	private AbstractActionableObject getNextTarget(Toroidal2DPhysics space, Ship ship){
		AbstractActionableObject nextTarget = null;
		//find the traitor shooting the base, if there is one. Otherwise, get the next target
		nextTarget = Combat.getEnemyNearBase(space, ship);
		if(nextTarget == null){
			nextTarget = Combat.findNearestEnemyBase(space,ship);
		}
		if(nextTarget == null){
			nextTarget = Combat.nearestEnemy(space, ship);
		}
		
		return nextTarget;
	}
	
	private boolean isGoodTarget(Toroidal2DPhysics space, AbstractActionableObject target){
		if(target != null && target.isAlive()){
			//If it's a base with low energy, don't target it anymore
			if(target.getMaxEnergy() == Base.INITIAL_BASE_ENERGY && target.getEnergy() < 500){
				return false;
			}
			//Otherwise, it's a ship or a base with lots of energy, so KILL IT WITH FIRE
			return true;
		}
		return false;
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
		framesSinceLastShot++;
		
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
			
			boolean bulletsWontCollide = framesSinceLastShot >= shootingDelay;
			
			if (actionableObject.isValidPowerup(powerup) && shouldShoot && bulletsWontCollide){
				powerUps.put(actionableObject.getId(), powerup);
				framesSinceLastShot = 0;
			}
		}
		
		
		return powerUps;
	}

}
