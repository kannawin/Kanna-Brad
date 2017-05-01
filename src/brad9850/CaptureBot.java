package brad9850;

import java.awt.Color;
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
import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.StarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Flag;
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
 * 
 * @author Christopher Bradford & Scott Kannawin
 */
public class CaptureBot extends TeamClient {
	boolean shouldShoot = false;
	
	UUID targetID = null;
	ArrayList<Position> path = new ArrayList<Position>();
	
	int lastTimestep = 0;
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	
	//Magic numbers
	public final int EnergyThreshold = 1500;
	public final int PathingFrequency = 25;
	public final boolean Drawing = true;
	
	Position previousPosition = null;
	UUID previousMovementTargetUUID = null;
	
	int sum = 0;
	int count = 0;
	int previousTimestep = 0;
	
	ArrayList<UUID> ships = new ArrayList<UUID>();
	HashMap<UUID,ArrayList<Position>> paths = new HashMap<UUID,ArrayList<Position>>();
	AbstractObject[] targets;
	UUID currentShip = null;
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
				
				UUID tempShip = ship.getId();
				if(ships.indexOf(tempShip) == -1){
					ships.add(tempShip);
					paths.put(tempShip, null);
				}
				
				AbstractAction[] action = getAction(space, ship);
				
				//AbstractAction action = new DoNothingAction();
				
				for(int i = 0; i < this.ships.size();i++){
					actions.put(this.ships.get(i),action[i]);
				}
				
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
	private AbstractAction[] getAction(Toroidal2DPhysics space, Ship ship) {
		//nullify from previous action
		ship.setCurrentAction(null);
		
		AbstractAction[] newAction = new AbstractAction[this.ships.size()];

		//Find a default place to move to.
		AbstractObject movementGoal = Combat.nearestBeacon(space, ship);
		

		
//		//If we're low on energy, look for a beacon close by
//		if(ship.getEnergy() < EnergyThreshold){
//			movementGoal = Combat.nearestBeacon(space, ship);
//		}
		
		previousMovementTargetUUID = movementGoal.getId();
		//If it's time to generate a new path, do it
		if(space.getCurrentTimestep() - this.lastTimestep > PathingFrequency
				|| this.path.size() == 0
				|| previousMovementTargetUUID != movementGoal.getId() ){
			
			this.targets = Actions.getActions(space, this.ships);
			for(UUID ships : this.paths.keySet()){
				this.paths.put(ships, Pathing.findPath(space, ship, this.targets[this.ships.indexOf(ships)]));
			}
			
			this.lastTimestep = space.getCurrentTimestep();
			
		}
		
		
		
		for(int i =0 ; i<this.ships.size();i++){
		
			ArrayList<Position> currentPath = this.paths.get(this.ships.get(i));
			
			//Get a waypoint to move to
			//If we're already really close to it, find the next target
			Position waypoint = currentPath.get(0);
			
			while(waypoint != null && space.findShortestDistance(ship.getPosition(), waypoint) < ship.getRadius() * 2){
				currentPath.remove(0);
				waypoint = null;
				if(this.path.size() > 0){
					waypoint = currentPath.get(0);
				}
			}
			
			//If we have no other waypoint, aim at our target
			if(waypoint == null){
				waypoint = movementGoal.getPosition();
			}
			
			//Get the movement to our waypoint
			int distanceFactor = 150;
			newAction[i] = Vectoring.advancedMovementVector(space, ship, waypoint, false, distanceFactor);
			
			
	//		//Decide if we should shoot		
	//		if(Combat.willMakeItToTarget(space, ship, target, target.getPosition().getTranslationalVelocity())){
	//			shouldShoot= true;
	//		}
	//		else{
	//			shouldShoot = false;
	//		}
			
			if(Drawing){
				drawPath(space, ship);
			}
			
			this.paths.put(this.ships.get(i), currentPath);
				
		}
		return newAction;
	}
	
	/**
	 * Get a target to hunt
	 * @param space
	 * @param ship
	 * @return
	 */
	private UUID findTarget(Toroidal2DPhysics space, Ship ship){
		return Combat.nearestEnemy(space, ship).getId();
	}
	
	/**
	 * See if we should still chase our current target
	 * @param space
	 * @param targetID
	 * @return
	 */
	private boolean isValidTarget(Toroidal2DPhysics space, UUID targetID){
		return (targetID != null
				&& space.getObjectById(targetID) != null
				&& space.getObjectById(targetID).isAlive());
	}
	
	/**
	 * Draw a line directly connecting the ship and its target, and a bunch of lines connecting the path nodes between the two
	 * @param space
	 * @param ship
	 */
	private void drawPath(Toroidal2DPhysics space, Ship ship){
		//Don't try to draw a path that doesn't exist
		if(path.size() == 0){
			return;
		}
		
		Position shipPosition = ship.getPosition();
		Position targetPosition = this.path.get(this.path.size() - 1);
		
		LineGraphics targetLine = new LineGraphics(shipPosition, targetPosition, space.findShortestDistanceVector(shipPosition, targetPosition));
		targetLine.setLineColor(Color.RED);
		graphicsToAdd.add(targetLine);
		
		for(int i = 0; i < path.size(); i++){
			//TODO: Solve root cause of this, and of all evil
//			if(space.getObjectById(path.get(i)) == null){
//				break;
//			}
			Position thisPosition = path.get(i);
			Position previousPosition = ship.getPosition();
			if(i > 0){
				previousPosition = path.get(i - 1);
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