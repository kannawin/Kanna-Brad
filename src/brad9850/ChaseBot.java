package brad9850;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
		
		//find the traitor shooting the base, if there is one, else get the next target
		AbstractObject traitor = Combat.getEnemyNearBase(space, ship);
		AbstractObject nextTarget = Combat.findNearestEnemyBase(space,ship);
		AbstractObject nearestEnemy = Combat.nearestEnemy(space, ship);

		
		//Don't want to shoot beacons when searching for them
		shouldShoot = false;
		if(ship.getEnergy() > 1750){

			if(traitor != null){
				if(Combat.isAimingAtTarget(space, ship, traitor))
					shouldShoot = true;
				newAction = Vectoring.advancedMovementVector( space, ship, traitor, 200);
			}
			else if (nextTarget != null){ 
				if(Combat.isAimingAtTarget(space, ship, nextTarget))
					shouldShoot = true;
				newAction = Vectoring.advancedMovementVector(space,ship,nextTarget, 200);	
			}
			else if (nearestEnemy != null){
				if(Combat.willHitMovingTarget(space, ship, nearestEnemy, nearestEnemy.getPosition().getTranslationalVelocity())){
					shouldShoot = true;
				}
				newAction = Vectoring.advancedMovementVector(space,ship,nearestEnemy, 200);
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