package brad9850;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Modification of the aggressive heuristic asteroid collector to a team that only has one ship.  It 
 * tries to collect resources but it also tries to shoot other ships if they are nearby.
 * 
 * @author amy
 */
public class BaseBot extends TeamClient {
	boolean shouldShoot = false;
	int framesSinceLastShot = 0;
	UUID currentTarget = null;

	/**
	 * Assigns ships to asteroids and beacons, as described above
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
	private AbstractAction getAction(Toroidal2DPhysics space,
			Ship ship) {
		DrawFunctions.Refresh();
		
		AbstractAction current = ship.getCurrentAction();
		Position currentPosition = ship.getPosition();

		AbstractAction newAction = null;
		
		Base targetBase = (Base)space.getObjectById(currentTarget); 
		
		//If our target is nearly dead, find a new one
		if(targetBase == null || targetBase.getEnergy() < 400){ //Keep relatively high because there's probably several bullets already on their way
			double maxEnergy = 0;
			for(Base base : space.getBases()){
				if(!base.getTeamName().equalsIgnoreCase(ship.getTeamName())){
					double baseEnergy = base.getEnergy();
					if(baseEnergy > maxEnergy){
						maxEnergy = baseEnergy;
						targetBase = base;
						currentTarget = base.getId();
					}
				}
			}
		}
		
		shouldShoot = false;
		if(Combat.isAimingAtTarget(space, ship, targetBase)){
			if(Combat.willMakeItToTarget(space, ship, targetBase, targetBase.getPosition().getTranslationalVelocity())){
				shouldShoot = true;
			}
		}		
		
		newAction = new MoveToObjectAction(space, currentPosition, targetBase);		
		return newAction;
	}
	
	/**
	 * 
	 * @param space
	 * @param target
	 * @return
	 */
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
		HashSet<SpacewarGraphics> graphics = new HashSet<SpacewarGraphics>();
		graphics.addAll(DrawFunctions.GetGraphics());
		DrawFunctions.Refresh();
		return graphics;
	}

	@Override
	/**
	 * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
	 * far away from the existing bases
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
