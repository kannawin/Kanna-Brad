package brad9850;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.StarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
/**
 * A* based Agent that only hunts down the nearest enemy
 * It traverses using distance between nodes (mineable asteroids, and beacons)
 * The heuristic function is direct distance to the target
 * It gets a path by seeing if between asteroid is a non mineable asteroid and deletes that edge
 * 
 * @author Christopher Bradford & Scott Kannawin
 */
public class CaptureBot extends TeamClient {	
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	
	//Magic numbers
	public final int EnergyThreshold = 1500;
	public final int PathingFrequency = 25;
	public final boolean Drawing = true;
	
	//Arrays for handling multiple ships
	ArrayList<UUID> ships = new ArrayList<UUID>();
	HashMap<UUID,ArrayList<Position>> paths = new HashMap<UUID,ArrayList<Position>>();
	ArrayList<AbstractObject> targets = new ArrayList<AbstractObject>();
	ArrayList<Integer> lastPathfindTimestep = new ArrayList<Integer>();
	ArrayList<UUID> previousMovementTargetIDs = new ArrayList<UUID>();
	ArrayList<Boolean> shouldShoot = new ArrayList<Boolean>();
	
	UUID currentShipID = null;
	int currentShipIndex = -1;
	/**
	 * 
	 */
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		graphicsToAdd = new ArrayList<SpacewarGraphics>();
		// loop through each ship
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				currentShipID = ship.getId();
				if(ships.indexOf(currentShipID) == -1){
					this.ships.add(currentShipID);
					this.paths.put(currentShipID, new ArrayList<Position>());
					this.targets.add(ship);
					this.lastPathfindTimestep.add(0);
					this.previousMovementTargetIDs.add(null);
					this.shouldShoot.add(false);
				}
				currentShipIndex = ships.indexOf(currentShipID);
				actions.put(currentShipID, getAction(space,ship));
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
		//nullify from previous action
		ship.setCurrentAction(null);
		
		AbstractAction newAction = new DoNothingAction();

		//Find a default place to move to.
		AbstractObject movementGoal = Combat.nearestBeacon(space, ship);
		boolean solidGoal = false;		
		
		//Pick somewhere to go to
		movementGoal = Actions.getActions(space, this.ships, ship.getId(), this.targets);
		//Let the other ships know where we're going
		this.targets.set(currentShipIndex, movementGoal);
		
		//See if our goal has changed, to know whether to make a new path
		boolean goalChanged = false;
		if(movementGoal.getId() != previousMovementTargetIDs.get(currentShipIndex)){
			goalChanged = true;
		}
		
		//Decide if we should shoot	
		if (movementGoal instanceof Ship &&
				Combat.willMakeItToTarget(space, ship, movementGoal, movementGoal.getPosition().getTranslationalVelocity())) {
			shouldShoot.set(currentShipIndex, true);
		} else {
			shouldShoot.set(currentShipIndex, false);
		}
		
		//Draw what we need to
		if(Drawing){
			drawPath(space, ship);
		}
		
		//Figure out how to get to our destination
		newAction = getMovementAction(space, ship, movementGoal.getPosition(), solidGoal, goalChanged);
		previousMovementTargetIDs.set(currentShipIndex, movementGoal.getId());
		
		return newAction;
	}
	
	private AbstractAction getMovementAction(Toroidal2DPhysics space, Ship ship, Position goalPosition, boolean solidGoal, boolean goalChanged){
		AbstractAction movementAction = new DoNothingAction();
		int distanceFactor = 150;
		
		Set<AbstractObject> obstructions = Pathing.findObstructions(space, goalPosition);
		
		if(space.isPathClearOfObstructions(ship.getPosition(), goalPosition, obstructions, ship.getRadius())){
			//If the path to the goal is clear, go straight there
			movementAction = Vectoring.advancedMovementVector(space, ship, goalPosition, solidGoal, distanceFactor);
			//We don't need this functionally, but it does fix drawing the path
			this.paths.get(currentShipID).clear();
			this.paths.get(currentShipID).add(goalPosition);
		}
		else{
			//Otherwise, make a path towards the target
			
			//If it's time to generate a new path, do it
			if(space.getCurrentTimestep() - this.lastPathfindTimestep.get(currentShipIndex) > PathingFrequency
					|| this.paths.get(currentShipID).size() == 0
					|| goalChanged){
				this.lastPathfindTimestep.set(currentShipIndex, space.getCurrentTimestep());
				this.paths.put(currentShipID, Pathing.findPath(space, ship, goalPosition));
			}

			//Get a waypoint to move to
			//If we're already really close to it, find the next target
			Position waypoint = this.paths.get(currentShipID).get(0);
			boolean solidWaypoint = false;
			while(waypoint != null && space.findShortestDistance(ship.getPosition(), waypoint) < ship.getRadius() * 2){
				this.paths.get(currentShipID).remove(0);
				waypoint = null;
				if(this.paths.get(currentShipID).size() > 0){
					waypoint = this.paths.get(currentShipID).get(0);
				}
			}
			
			//If we have no other waypoint, aim at our target
			if(waypoint == null){
				waypoint = goalPosition;
				solidWaypoint = true;
			}

			//Get the movement to our waypoint
			movementAction = Vectoring.advancedMovementVector(space, ship, waypoint, solidWaypoint, distanceFactor);
		}
		
		return movementAction;
	}
	
//	/**
//	 * Get a target to hunt
//	 * @param space
//	 * @param ship
//	 * @return
//	 */
//	private UUID findTarget(Toroidal2DPhysics space, Ship ship){
//		return Combat.nearestEnemy(space, ship).getId();
//	}
//	
//	/**
//	 * See if we should still chase our current target
//	 * @param space
//	 * @param targetID
//	 * @return
//	 */
//	private boolean isValidTarget(Toroidal2DPhysics space, UUID targetID){
//		return (targetID != null
//				&& space.getObjectById(targetID) != null
//				&& space.getObjectById(targetID).isAlive());
//	}
	
	/**
	 * Draw a line directly connecting the ship and its target, and a bunch of lines connecting the path nodes between the two
	 * @param space
	 * @param ship
	 */
	private void drawPath(Toroidal2DPhysics space, Ship ship){
		//Don't try to draw a path that doesn't exist
		if(this.paths.get(currentShipID).size() == 0){
			return;
		}
		
		Position shipPosition = ship.getPosition();
		Position targetPosition = this.paths.get(currentShipID).get(this.paths.get(currentShipID).size() - 1);
		
		LineGraphics targetLine = new LineGraphics(shipPosition, targetPosition, space.findShortestDistanceVector(shipPosition, targetPosition));
		targetLine.setLineColor(Color.RED);
		graphicsToAdd.add(targetLine);
		
		for(int i = 0; i < this.paths.get(currentShipID).size(); i++){
			//TODO: Solve root cause of this, and of all evil
//			if(space.getObjectById(path.get(i)) == null){
//				break;
//			}
			Position thisPosition = this.paths.get(currentShipID).get(i);
			Position previousPosition = ship.getPosition();
			if(i > 0){
				previousPosition = this.paths.get(currentShipID).get(i - 1);
			}
			
			graphicsToAdd.add(new StarGraphics(3, Color.WHITE, thisPosition));
			LineGraphics line = new LineGraphics(previousPosition, thisPosition, space.findShortestDistanceVector(previousPosition, thisPosition));
			line.setLineColor(Color.WHITE);
			graphicsToAdd.add(line);
		}
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
		graphics.addAll(graphicsToAdd);
		graphicsToAdd.clear();
		return graphics;
	}

	@Override
	/**
	 * Buy things sometimes
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, 
			ResourcePile resourcesAvailable, 
			PurchaseCosts purchaseCosts) {

		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();
		
		//if you can afford something worthwhile (ship or base)
		if(purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable)
				|| purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)){
			//will buy ship if its the cheapest option
			boolean buyShip = purchaseCosts.getCost(PurchaseTypes.BASE).greaterThan(purchaseCosts.getCost(PurchaseTypes.SHIP));
			if(buyShip){
				for(AbstractActionableObject actionableObject : actionableObjects){
					if(actionableObject instanceof Base){
						Base base = (Base) actionableObject;
						purchases.put(base.getId(), PurchaseTypes.SHIP);
					}
				}
			}
			//else base will be bought
			else{
				boolean canplace = true;
				for(AbstractActionableObject actionableObject : actionableObjects){
					if(actionableObject instanceof Ship && actionableObject.getId() == this.ships.get(0)){
						//base can only be bought if its within a certain distance from another base
						for(AbstractActionableObject actionableObject2 : actionableObjects){
							if(actionableObject2 instanceof Base){
								if(space.findShortestDistance(actionableObject2.getPosition(), space.getObjectById(this.ships.get(0)).getPosition()) < 250){
									canplace = false;
									break;
								}
							}
						}
						if(canplace){
							Ship ship = (Ship) actionableObject;
							purchases.put(ship.getId(), PurchaseTypes.BASE);
						}
					}
				}
			}
		}

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

		for (AbstractActionableObject actionableObject : actionableObjects) {
			if (actionableObject instanceof Ship) {
				Ship ship = (Ship) actionableObject;
				int shipIndex = ships.indexOf(ship.getId());
				
				SpaceSettlersPowerupEnum powerup = SpaceSettlersPowerupEnum.FIRE_MISSILE;

				// Shoot less often when we're moving fast to prevent our
				// bullets from colliding with each other
				// TODO: Only limit this if we're aiming in the same direction
				// we're traveling
				double vx = ship.getPosition().getxVelocity();
				double vy = ship.getPosition().getyVelocity();
				double shipSpeed = Math.sqrt(vx * vx + vy * vy);
				int shootingDelay = 2 + (int) ((shipSpeed - 15) / 15);

				// If the ship is close to going as fast as a missile, don't
				// shoot
				if (shipSpeed + 10 > Missile.INITIAL_VELOCITY) {
					shootingDelay = Integer.MAX_VALUE;
				}

				boolean bulletsWontCollide = space.getCurrentTimestep() % shootingDelay == 0;
				
				if (ship.isValidPowerup(powerup) && this.shouldShoot.get(shipIndex) && bulletsWontCollide) {
					powerUps.put(ship.getId(), powerup);
				}
			}
		}
		
		
		return powerUps;
	}

}
