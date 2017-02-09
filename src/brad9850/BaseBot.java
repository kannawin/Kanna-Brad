package brad9850;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import spacesettlers.actions.*;
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
import spacesettlers.utilities.Vector2D;
import spacesettlers.clients.TeamClient;
import brad9850.Vectoring;
import brad9850.Combat;
import brad9850.DrawFunctions;

/**
 * Modification of the aggressive heuristic asteroid collector to a team that only has one ship.  It 
 * tries to collect resources but it also tries to shoot other ships if they are nearby.
 * 
 * @author amy
 */
public class BaseBot extends TeamClient {
	boolean shouldShoot = false;

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
		
		
		Base targetBase = findNearestEnemyBase(space, ship);
		shouldShoot = false;
		if(Combat.isAimingAtTarget(space, ship, targetBase)){
			shouldShoot = true;
		}
		
		
		newAction = new MoveToObjectAction(space, currentPosition, targetBase);		
		return newAction;
	}
	
	/**
	 * Find the base for an enemy team nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	private Base findNearestEnemyBase(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		Base nearestBase = null;
		
		//First, try to find the closest enemy base that belongs to a human team
		for (Base base : space.getBases()) {
			if (Combat.isHumanEnemyTeam(base.getTeamName(), ship.getTeamName())) {
				double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
				if (dist < minDistance) {
					minDistance = dist;
					nearestBase = base;
				}
			}
		}
		
		//If there is no other human enemy, pick the closest AI base
		if(nearestBase == null){
			for (Base base : space.getBases()) {
				if (!base.getTeamName().equalsIgnoreCase(ship.getTeamName())) {
					double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
					if (dist < minDistance) {
						minDistance = dist;
						nearestBase = base;
					}
				}
			}
		}
		
		return nearestBase;
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
	 * The aggressive asteroid collector shoots if there is an enemy nearby! 
	 * 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, SpaceSettlersPowerupEnum> powerUps = new HashMap<UUID, SpaceSettlersPowerupEnum>();

		Random random = new Random();
		for (AbstractActionableObject actionableObject : actionableObjects){
			SpaceSettlersPowerupEnum powerup = SpaceSettlersPowerupEnum.FIRE_MISSILE;
			
			
			//Shoot less often when we're moving fast to prevent our bullets from colliding with each other
			//TODO: Only limit this if we're aiming in the same direction we're traveling
			double maxAxisSpeed = Math.max(Math.abs(actionableObject.getPosition().getxVelocity()), Math.abs(actionableObject.getPosition().getyVelocity()));
			int shootingDelay = 2 + (int)((maxAxisSpeed - 15)/15);
			
			//If the ship is close to going as fast as a missile, don't shoot
			if(maxAxisSpeed + 10 > Missile.INITIAL_VELOCITY){
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
