package kann0200;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
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
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	private int steps = 0;
	private int evalSteps = 1999;
	
	private GAPopulation population;
	private ArrayList<UUID> positions = new ArrayList<UUID>();
	private int popSize = 20;
	private int distanceThreshold = 150;
	private int deltaDeath = 0;
	private int deltaKill = 0;
	private double velocity = Movement.MAX_TRANSLATIONAL_ACCELERATION;
	boolean doneAction = false;
	private int generation = 0;

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
				shouldShoot = false;
				drawPath(space,ship);
				
				AbstractAction action;
				if(this.doneAction || this.positions.size() == 0 || (positions.size() < 1 && space.getObjectById(this.positions.get(this.positions.size() - 1)).isAlive() == false)){
					//get new movementmap
					GAState newState = new GAState(space,ship);
					this.positions = newState.returnNextPosition(space,ship);
					//has a 1/25 chance of doing something random
					//policy.getCurrentAction(space, ship, newState, 25);
					action =  Vectoring.advancedMovementVector(space, ship, space.getObjectById(this.positions.get(0)), this.distanceThreshold, this.velocity);
				}
				else{
					try{
						//continue movement map
						if( space.findShortestDistance(ship.getPosition(), space.getObjectById(this.positions.get(0)).getPosition()) < 50
								&& space.getObjectById(this.positions.get(0)).getClass() != Ship.class){
							action = Vectoring.advancedMovementVector(space, ship, space.getObjectById(this.positions.get(0)), this.distanceThreshold, this.velocity);
							this.positions.remove(0);
						}
						//is a ship
						else{
							if(space.getObjectById(this.positions.get(this.positions.size()-1)).isAlive() && ship.getEnergy() > 1500){
								action = Vectoring.advancedMovementVector(space, ship, space.getObjectById(this.positions.get(0)), 10, this.velocity);
								if(Combat.willHitMovingTarget(space, ship, space.getObjectById(this.positions.get(this.positions.size() - 1)), space.getObjectById(this.positions.get(this.positions.size() - 1)).getPosition().getTranslationalVelocity())){
									shouldShoot= true;
								}
							}
							else{
								//need to get a new movement map
								GAState newState = new GAState(space,ship);
								this.positions = newState.returnNextPosition(space,ship);
								//policy.getCurrentAction(space, ship, newState, 25);
								action = Vectoring.advancedMovementVector(space, ship, space.getObjectById(this.positions.get(0)), this.distanceThreshold, this.velocity);
							}
						}
					}
					catch(NullPointerException e){
						GAState newState = new GAState(space,ship);
						this.positions = newState.returnNextPosition(space, ship);
						action = Vectoring.advancedMovementVector(space, ship, space.getObjectById(this.positions.get(0)), this.distanceThreshold, this.velocity);
					}
				}

				actions.put(ship.getId(), action);
				
			} else {
				// it is a base.  Heuristically decide when to use the shield (TODO)
				actions.put(actionable.getId(), new DoNothingAction());
			}
		} 
		return actions;
	}
	
	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		//TODO GENETIC ALGORITHM END
		steps++;
		//evaluates 
		if (steps % evalSteps == 0) {
			
			for (AbstractObject actionable :  actionableObjects) {
				if (actionable instanceof Ship) {
					Ship ship = (Ship) actionable;
					// note that this method currently scores every policy as zero as this is part of 
					// what the student has to do
					population.evaluateFitnessForCurrentMember(space, ship, this.deltaDeath, this.deltaKill, this.velocity);
					this.deltaDeath = ship.getKillsReceived();
					this.deltaKill = ship.getKillsInflicted();
					this.velocity = population.nextVelocity();
				}
			}
		
			// move to the next member of the population
			population.getNextMember();

			if (population.isGenerationFinished()) {
				// note that this is also an empty method that a student needs to fill in
				population.makeNextGeneration();
				
				population.getNextMember();
			}
			
		}
	}
	
	
	
	
	
	
	/**
	 * Draw a line directly connecting the ship and its target, and a bunch of lines connecting the path nodes between the two
	 * @param space
	 * @param ship
	 */
	private void drawPath(Toroidal2DPhysics space, Ship ship){
		//Don't try to draw a path that doesn't exist
		if(this.positions.size() == 0){
			return;
		}
		
		Position shipPosition = ship.getPosition();
		Position targetPosition= ship.getPosition();
		try{
			targetPosition = space.getObjectById(this.positions.get(this.positions.size() - 1)).getPosition();
		}
		catch(NullPointerException e){
			targetPosition = ship.getPosition();
		}
		LineGraphics targetLine = new LineGraphics(shipPosition, targetPosition, space.findShortestDistanceVector(shipPosition, targetPosition));
		targetLine.setLineColor(Color.RED);
		graphicsToAdd.add(targetLine);
		
		for(int i = 0; i < positions.size(); i++){
			//TODO: Solve root cause of this, and of all evil
			if(space.getObjectById(positions.get(i)) == null){
				break;
			}
			Position thisPosition = space.getObjectById(positions.get(i)).getPosition();
			Position previousPosition = ship.getPosition();
			if(i > 0){
				previousPosition = space.getObjectById(positions.get(i - 1)).getPosition();
			}
			
			graphicsToAdd.add(new StarGraphics(3, Color.WHITE, thisPosition));
			LineGraphics line = new LineGraphics(previousPosition, thisPosition, space.findShortestDistanceVector(previousPosition, thisPosition));
			line.setLineColor(Color.WHITE);
			graphicsToAdd.add(line);
		}
	}
	
	

	@Override
	public void initialize(Toroidal2DPhysics space) {
		population = new GAPopulation(this.popSize);
		population.setGeneration("kanna-brad.txt");
		this.velocity = population.getVelocity();
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