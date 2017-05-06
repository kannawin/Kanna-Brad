package brad9850;

import java.util.PriorityQueue;

import spacesettlers.objects.Asteroid;
import spacesettlers.simulator.Toroidal2DPhysics;

public class Planning {	
	public static final int Nothing = -1;
	public static final int GetFlag = 20;
	public static final int Standby = 21;	
	public static final int GoToBase = 22;
	public static final int BuyShip = 30;
	public static final int BuyBase = 31;

	private static final int NumShips = 4;
	private static final int NumAsteroids = 10;
	
	private static final int MaxSearchLength = 750;
	
	public static int[] plan(Toroidal2DPhysics space, PlanState state, int flagsAhead){
		int[] firstActions = new int[NumShips + 1];
		
		PriorityQueue<PlanState> frontier = new PriorityQueue<PlanState>(new PlanStateComparator());
		frontier.add(state);
		
		int startingFlags = state.flagCount;

		PlanState currentState = frontier.poll();
		int searchLength = 0;

		while (currentState != null 
				&& currentState.flagCount < startingFlags + flagsAhead
				&& searchLength++ < MaxSearchLength) {
			// Attempt to take all possible actions, and add valid ones to the
			// queue
			for (int ship = 0; ship < NumShips; ship++) {
				// Mining asteroids
				for (int asteroid = 0; asteroid < NumAsteroids; asteroid++) {
					PlanState stateAfterAction = mineAsteroid(space, currentState, ship, asteroid);
					if (stateAfterAction != null) {
						frontier.add(stateAfterAction);
					}
				}
				// Going to locations
				PlanState[] movingActions = new PlanState[3];
				movingActions[0] = pickupFlag(currentState, ship);
				movingActions[1] = returnToBase(currentState, ship);
				movingActions[2] = standby(currentState, ship);

				for (int i = 0; i < movingActions.length; i++) {
					if (movingActions[i] != null) {
						frontier.add(movingActions[i]);
					}
				}
			}
			// Trying to buy something
			PlanState[] buyingActions = new PlanState[2];
			buyingActions[1] = buyShip(currentState);
			buyingActions[0] = buyBase(currentState);
			for (int i = 0; i < buyingActions.length; i++) {
				if (buyingActions[i] != null) {
					frontier.add(buyingActions[i]);
				}
			}

			currentState = frontier.poll();
		}
		
		for(int i = 0; i < currentState.shipFirstActions.length; i++){
			firstActions[i] = currentState.shipFirstActions[i];
		}
		firstActions[NumShips] = currentState.firstPurchase;
		return firstActions;
	}
	
	/**
	 * Send a ship to mine a certain asteroid
	 * @param state
	 * @param ship
	 * @param asteroid
	 * @return
	 */
	private static PlanState mineAsteroid(Toroidal2DPhysics space, PlanState oldState, int ship, int asteroid){
		PlanState state = new PlanState(oldState);
		if(state.shipBought[ship] 
				&& state.shipCarryingFlag != ship 
				&& state.asteroidClaimedBy[asteroid] == -1){
			Asteroid asteroidObject = (Asteroid) space.getObjectById(state.asteroidID.get(asteroid));
			
			state.asteroidClaimedBy[asteroid] = ship;
			state.shipCarrying[ship].add(asteroidObject.getResources());
			updateDuration(state, ship, state.estimatedTimeToAsteroid);
			
			if(state.shipFirstActions[ship] == Planning.Nothing){
				state.shipFirstActions[ship] = asteroid;
			}
			
			return state;
		}
		else{
			return null;
		}
	}
	
	/**
	 * Buy a ship
	 * @param oldState
	 * @return
	 */
	private  static PlanState buyShip(PlanState oldState){
		PlanState state = new PlanState(oldState);
		if(!state.shipBought[state.shipBought.length - 1] //Don't buy a ship if we already have our max
				&& state.totalResources.greaterThan(state.shipCost)){
			
			//Figure out the next ship we need to buy and buy it
			for(int i = 0; i < state.shipBought.length; i++){
				if(!state.shipBought[i]){
					state.shipBought[i] = true;
				}
			}
			state.shipCount++;
			//Subtract the cost of the ship, and increase it for next time
			state.totalResources.subtract(state.shipCost);
			state.shipCost.doubleCosts();
			
			if(state.firstPurchase == Planning.Nothing){
				state.firstPurchase = Planning.BuyShip;
			}
			
			return state;
		}else{
			return null;
		}
	}
	
	/**
	 * Buy a base
	 * @param oldState
	 * @return
	 */
	private static PlanState buyBase(PlanState oldState){
		PlanState state = new PlanState(oldState);
		if(state.baseCount < 3
				&& state.totalResources.greaterThan(state.baseCost)){
			//"Buy" the base
			state.baseCount++;
			state.estimatedTimeToBase -= 100;
			
			//Subtract the cost of the base, and increase it for next time
			state.totalResources.subtract(state.baseCost);
			state.baseCost.doubleCosts();
			
			if(state.firstPurchase == Planning.Nothing){
				state.firstPurchase = Planning.BuyBase;
			}
			
			return state;
		}else{
			return null;
		}
	}
	
	/**
	 * Have a ship pickup the flag
	 * @param oldState
	 * @param ship
	 * @return
	 */
	private static PlanState pickupFlag(PlanState oldState, int ship){
		PlanState state = new PlanState(oldState);
		if(state.shipBought[ship]
				&& state.shipCarryingFlag == -1){
			state.shipCarryingFlag = ship;
			
			//If this ship was waiting to pick up the flag, it will get there faster, and there will no longer be anyone left waiting
			int timeTaken = state.estimatedTimeToFlag;
			if(state.shipOnStandby == ship){
				state.shipOnStandby = -1;
				timeTaken -= 200;
			}
			updateDuration(state, ship, timeTaken);
			
			if(state.shipFirstActions[ship] == Planning.Nothing){
				state.shipFirstActions[ship] = Planning.GetFlag;
			}
			
			return state;
		}
		else{
			return null;
		}
	}
	
	/**
	 * Put a ship on standby for waiting for the flag
	 * @param oldState
	 * @param ship
	 * @return
	 */
	private static PlanState standby(PlanState oldState, int ship){
		PlanState state = new PlanState(oldState);
		if(state.shipBought[ship]
				&& state.shipCarryingFlag != -1
				&& state.shipOnStandby == -1){
			state.shipOnStandby = ship;
			
			if(state.shipFirstActions[ship] == Planning.Nothing){
				state.shipFirstActions[ship] = Planning.Standby;
			}
			
			return state;
		}
		else{
			return null;
		}
	}
	
	/**
	 * Return a ship to a base and 
	 * @param oldState
	 * @param ship
	 * @return
	 */
	private static PlanState returnToBase(PlanState oldState, int ship){
		PlanState state = new PlanState(oldState);
		if(state.shipBought[ship]
				&& state.shipOnStandby != ship){
			
			//Remove any resources the ship might have
			state.totalResources.add(state.shipCarrying[ship]);
			state.shipCarrying[ship].reset();
			//Return the flag
			state.flagCount += 1;
			state.shipCarryingFlag = -1;
			//Update the time
			updateDuration(state, ship, state.estimatedTimeToBase);
			//Have the standby ship start acting now
			if(state.shipOnStandby != -1){
				state.shipOccupiedUntil[state.shipOnStandby] = state.shipOccupiedUntil[ship];
			}
			
			if(state.shipFirstActions[ship] == Planning.Nothing){
				state.shipFirstActions[ship] = Planning.GoToBase;
			}
			
			return state;
		}
		else{
			return null;
		}
		
	}
	
	/**
	 * Update the duration of the ship and overall plan
	 * @param state
	 * @param ship
	 * @param actionDuration
	 */
	private static void updateDuration(PlanState state, int ship, int actionDuration){
		state.shipOccupiedUntil[ship] += actionDuration;
		if(state.shipOccupiedUntil[ship] > state.totalDuration){
			state.totalDuration = state.shipOccupiedUntil[ship];
		}
	}
}
