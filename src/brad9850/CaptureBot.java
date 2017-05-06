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
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Ship;
import spacesettlers.objects.Base;
import spacesettlers.objects.powerups.PowerupDoubleHealingBaseEnergy;
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
	public final int MaxShips = 4;
	public final int MaxAsteroids = 10;
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
	
	//Planning tools
	int[] plannedActions = new int[MaxShips + 1];
//	PurchaseCosts lastPurchaseCost = null;
//	ArrayList<Asteroid> bestAsteroids = new ArrayList<Asteroid>();
	
	/**
	 * 
	 */
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		graphicsToAdd = new ArrayList<SpacewarGraphics>();
		//Generate the current state
		PlanState teamState = new PlanState(space, actionableObjects.iterator().next().getTeamName());
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
				//Have the home base plan what 
				Base base = (Base) actionable;
				if(base.isHomeBase() && space.getCurrentTimestep() % 20 == 0){
					plannedActions = Planning.plan(space, new PlanState(space, base.getTeamName()), 2);
				}
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
		
		int plannedAction = plannedActions[ships.indexOf(ship.getId())];
		if(plannedAction >= 0
				&& plannedAction < MaxAsteroids){
			//Hack to pick the nth mineable asteroid
			for(Asteroid asteroid : space.getAsteroids()){
				if(asteroid.isMineable()){
					if(plannedAction-- == 0){
						movementGoal = asteroid;
					}
				}
			}
		}
		else{
			movementGoal = Actions.getPlannedAction(space, ships, ship.getId(), plannedAction);
		}
		
		
//		//Pick somewhere to go to, as long as we have enough energy
//		if(ship.isCarryingFlag() || !(ship.getEnergy() < 1500)){
//			movementGoal = Actions.getActions(space, this.ships, ship.getId(), this.bestAsteroids, this.targets);
//		}
		if(!ship.isCarryingFlag() && ship.getEnergy() < 1500){
			movementGoal = Combat.nearestBeacon(space, ship);
		}
		//Let the other ships know where we're going
		this.targets.set(currentShipIndex, movementGoal);
		
		//See if our goal has changed, to know whether to make a new path
		boolean goalChanged = false;
		if(movementGoal.getId() != previousMovementTargetIDs.get(currentShipIndex)){
			goalChanged = true;
		}
		
		//Decide if we should shoot
		if (Combat.isEnemy(movementGoal, ship)
				&& Combat.willMakeItToTarget(space, ship, movementGoal, movementGoal.getPosition().getTranslationalVelocity())) {
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
	
//	//Figure out what we should buy next
//	private PurchaseTypes decideNextPurchase(Toroidal2DPhysics space, String teamName){
//		//Count the number of friendly ships/bases
//		int shipCount = 0;
//		int baseCount = 0;
//		
//		for(Ship ship : space.getShips()){
//			if(ship.getTeamName().equalsIgnoreCase(teamName)){
//				shipCount += 1;
//			}
//		}
//		for(Base base : space.getBases()){
//			if(base.getTeamName().equalsIgnoreCase(teamName)){
//				baseCount += 1;
//			}
//		}
//		
//		PurchaseTypes nextPurchase = PurchaseTypes.SHIP;
//		
//		int shipCost = lastPurchaseCost.getCost(PurchaseTypes.SHIP).getTotal();
//		int baseCost = lastPurchaseCost.getCost(PurchaseTypes.BASE).getTotal();
//		int healingCost = lastPurchaseCost.getCost(PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED).getTotal();
//		
//		//Decide what to buy next
//		if (shipCount < this.MaxShips) {
//			if (baseCost < shipCost) {
//				nextPurchase = PurchaseTypes.BASE;
//			}
//			else{
//				nextPurchase = PurchaseTypes.SHIP;
//			}
//		}
//		else{
//			if(baseCount > 3 || healingCost < baseCost * .25){
//				nextPurchase = PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED; 
//			}
//			else{
//				nextPurchase = PurchaseTypes.BASE;
//			}
//		}
//		
//		return nextPurchase;
//	}
//	
//	//Plan what to buy next and what asteroids to mine to buy it
//	private ArrayList<Asteroid> planPurchases(Toroidal2DPhysics space, Base base){
//		//See how much the next thing we need to buy costs
//		ResourcePile nextPurchaseCost = lastPurchaseCost.getCost(decideNextPurchase(space, base.getTeamName()));
//		//Reduce the price by what we already have
//		nextPurchaseCost.subtract(base.getTeam().getAvailableResources());
//		for(Ship ship : space.getShips()){
//			if(ship.getTeamName().equalsIgnoreCase(base.getTeamName())){
//				nextPurchaseCost.subtract(ship.getResources());
//			}
//		}
//		
//		//Find the mineable asteroids
//		ArrayList<Asteroid> mineableAsteroids = new ArrayList<Asteroid>();
//		for(Asteroid asteroid : space.getAsteroids()){
//			if(asteroid.isMineable()){
//				mineableAsteroids.add(asteroid);
//			}
//		}
//		
//		//Figure out what asteroids we need to collect
//		//Iterative deepening DFS with max depth of 3 asteroids
//		ArrayList<Asteroid> asteroidsToCollect = new ArrayList<Asteroid>();
//		ResourcePile costLeft = new ResourcePile(nextPurchaseCost);
//		for(int i = 1; i < 4; i++){
//			asteroidsToCollect = findBestAsteroids(mineableAsteroids, nextPurchaseCost, i);
//			for(Asteroid asteroid : asteroidsToCollect){
//				costLeft.subtract(asteroid.getResources());
//			}
//			if(costLeft.getTotal() == 0){
//				break;
//			}
//		}
//		
//		//Highlight what asteroids we're going after
//		if (this.Drawing) {
//			for (Asteroid asteroid : asteroidsToCollect) {
//				LineGraphics targetLine = new LineGraphics(base.getPosition(), asteroid.getPosition(),
//						space.findShortestDistanceVector(base.getPosition(), asteroid.getPosition()));
//				targetLine.setLineColor(Color.BLUE);
//				graphicsToAdd.add(targetLine);
//			}
//		}
//		
//		return asteroidsToCollect;
//	}
	
	//Recursively find the best asteroids to collect to cover the cost
	private ArrayList<Asteroid> findBestAsteroids(ArrayList<Asteroid> mineableAsteroids, ResourcePile cost, int maxAsteroidsLeft){
		ArrayList<Asteroid> bestAsteroids = new ArrayList<Asteroid>();
		int bestCost = cost.getTotal();
		if (maxAsteroidsLeft > 0 && cost.getTotal() > 0) {
			for (int i = 0; i < mineableAsteroids.size(); i++) {
				Asteroid asteroid = mineableAsteroids.get(i);
				
				//See how much this asteroid reduces the cost by
				ResourcePile costLeft = new ResourcePile(cost);
				costLeft.subtract(asteroid.getResources());
				
				//See what the best asteroids that we haven't used yet are
				ArrayList<Asteroid> mineableAsteroidsRemaining = new ArrayList<Asteroid>(mineableAsteroids);
				mineableAsteroidsRemaining.remove(i);
				ArrayList<Asteroid> bestAsteroidsLeft = findBestAsteroids(mineableAsteroidsRemaining, costLeft, maxAsteroidsLeft - 1);
				
				//Figure out the cost with all of those asteroids
				for(Asteroid otherAsteroid : bestAsteroidsLeft){
					costLeft.subtract(otherAsteroid.getResources());
				}
				
				//If it's the new best cost, use these asteroids instead
				if(costLeft.getTotal() < bestCost){
					bestAsteroidsLeft.add(asteroid);
					bestAsteroids = bestAsteroidsLeft;
					bestCost = costLeft.getTotal();
				}
				
				if(bestCost == 0){
					break;
				}
			}
		}
		return bestAsteroids;
	}
	
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

//		this.lastPurchaseCost = purchaseCosts;
//		PurchaseTypes nextPurchase = decideNextPurchase(space, actionableObjects.iterator().next().getTeamName());
		PurchaseTypes nextPurchase = PurchaseTypes.NOTHING;
		if(plannedActions[MaxShips] == Planning.BuyBase){
			nextPurchase = PurchaseTypes.BASE;
		}
		else if(plannedActions[MaxShips] == Planning.BuyShip){
			nextPurchase = PurchaseTypes.SHIP;
		}
		
		if(purchaseCosts.canAfford(nextPurchase, resourcesAvailable)){
			//Purchasing a ship
			if(nextPurchase == PurchaseTypes.SHIP){
				for(AbstractActionableObject actionableObject : actionableObjects){
					if(actionableObject instanceof Base){
						Base base = (Base) actionableObject;
						purchases.put(base.getId(), PurchaseTypes.SHIP);
					}
				}
			}
			//Purchasing a base
			else if (nextPurchase == PurchaseTypes.BASE) {
				boolean canplace = true;
				for (AbstractActionableObject actionableObject : actionableObjects) {
					//Only let the flag carrier purchase a base
					if (actionableObject instanceof Ship && ((Ship)actionableObject).isCarryingFlag()) {
						Ship ship = (Ship) actionableObject;
						// The base should be placed far away from existing bases
						for (AbstractActionableObject actionableObject2 : actionableObjects) {
							if (actionableObject2 instanceof Base) {
								Base base = (Base) actionableObject2;
								if (space.findShortestDistance(base.getPosition(), ship.getPosition()) < 250){
									canplace = false;
									break;
								}
							}
						}
						if (canplace) {
							purchases.put(ship.getId(), PurchaseTypes.BASE);
							break;
						}
					}
				}
			}
			//Purchasing faster regeneration
			else if (nextPurchase == PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED){
				//Boost our main base first
				Base firstBase = null;
				boolean powerupBought = false;
				for(AbstractActionableObject actionableObject : actionableObjects){
					if(actionableObject instanceof Base){
						Base base = (Base) actionableObject;
						if(base.isHomeBase()){
							if(!base.getCurrentPowerups().contains(SpaceSettlersPowerupEnum.DOUBLE_BASE_HEALING_SPEED)){
								purchases.put(base.getId(), PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED);
								powerupBought = true;
							}
						}
						else if(firstBase == null){
							firstBase = base;
						}
					}
				}
				if(!powerupBought){
					purchases.put(firstBase.getId(), PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED);
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
