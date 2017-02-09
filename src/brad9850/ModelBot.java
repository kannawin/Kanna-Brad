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
	UUID currentTargetID = null;

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

		AbstractActionableObject currentTarget = (AbstractActionableObject) space.getObjectById(currentTargetID);
		
		//Don't want to shoot beacons when searching for them
		shouldShoot = false;
		if(ship.getEnergy() > 2000){
			//Hunt down a target
			if(currentTarget != null){
				System.out.println(currentTarget.getEnergy());
			}
			if(isGoodTarget(space, currentTarget)){
				//Use better aiming function if the target is going slow enough
				double speed = currentTarget.getPosition().getTranslationalVelocity().getMagnitude();
				if(speed < 5){
					if(Functions.isAimingAtTarget(space, ship, currentTarget)){
						shouldShoot = true;
					}
				}
				else{
					if(Functions.willHitMovingTarget(space, ship, currentTarget, currentTarget.getPosition().getTranslationalVelocity())){
						shouldShoot = true;
					}
				}
				newAction = Functions.advancedMovementVector( space, ship, currentTarget, 200);
			}
			else{
				getNextTargetID(space, ship);
			}
		}
		else{
			//Look for a beacon
			ship.getPosition().setAngularVelocity(Movement.MAX_ANGULAR_ACCELERATION);
			ship.getPosition().setOrientation(Functions.angleBetween(space, ship, Functions.nearestBeacon(space, ship)));
			newAction = Functions.advancedMovementVector(space, ship, Functions.nearestBeacon(space, ship), 150);
		}
		return newAction;
	}
	
	private void getNextTargetID(Toroidal2DPhysics space, Ship ship){
		AbstractActionableObject nextTarget = null;
		//find the traitor shooting the base, if there is one. Otherwise, get the next target
		nextTarget = Functions.getEnemyNearBase(space, ship);
		if(nextTarget == null){
			nextTarget = Functions.findNearestEnemyBase(space,ship);
		}
		if(nextTarget == null){
			nextTarget = Functions.nearestEnemy(space, ship);
		}
		
		currentTargetID = nextTarget.getId();
	}
	
	private boolean isGoodTarget(Toroidal2DPhysics space, AbstractActionableObject target){
		if(target != null && target.isAlive()){
			//If it's a base with low energy, don't target it anymore
			if((target instanceof Base) && target.getEnergy() < 500){
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
			double shipSpeed = actionableObject.getPosition().getTranslationalVelocity().getMagnitude();
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
