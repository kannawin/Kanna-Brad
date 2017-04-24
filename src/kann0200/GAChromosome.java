package kann0200;

import java.util.HashMap;
import java.util.Random;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;


public class GAChromosome {
	private HashMap<GAState, AbstractAction> policy;
	
	public GAChromosome() {
		policy = new HashMap<GAState, AbstractAction>();
	}

	/**
	 * Returns either the action currently specified by the policy or randomly selects one if this is a new state
	 * 
	 * @param currentState
	 * @return
	 */
	public void getCurrentAction(Toroidal2DPhysics space, Ship ship, GAState currentState, int rand) {
		AbstractObject target = space.getObjectById(currentState.returnNextPosition(space, ship).get(currentState.returnNextPosition(space, ship).size()));
		
		if (!policy.containsKey(currentState)) {
			if (new Random().nextInt(rand)==0) {
				//policy.put(currentState, Vectoring.advancedMovementVector(space, ship, Combat.nearestBeacon(space, ship), 150));
			} else {
				//policy.put(currentState, Vectoring.advancedMovementVector(space, ship, target, 150));
			}
		}

		//return policy.get(currentState);

	}
	
	
	
}
