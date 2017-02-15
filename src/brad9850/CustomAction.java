package brad9850;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import spacesettlers.actions.AbstractAction;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Vector2D;

public class CustomAction extends AbstractAction{
	
	protected double angularAcceleration;
	protected Vector2D translationalAcceleration;
	private boolean finished = false;
	private int timeFinishOffset = 0;
	private int timeStart;
	
	Stack<Double> stack = new Stack<Double>();
	
	/**
	 * Main instantiation of CustomAction, feed it space, the movement vector and rotational accel with a time offset
	 * time offset is for how long the action will last in time steps
	 * 
	 * @param space
	 * @param translationalAcceleration
	 * @param rotationalAcceleration
	 * @param offset
	 */
	public CustomAction(Toroidal2DPhysics space, Vector2D translationalAcceleration, double rotationalAcceleration, int offset) {
		super();
		this.translationalAcceleration = translationalAcceleration;
		this.timeStart = space.getCurrentTimestep();
		this.angularAcceleration = rotationalAcceleration;
		this.timeFinishOffset = space.getCurrentTimestep() + offset;
		this.finished = false;
	}
	public CustomAction(Vector2D translationalAcceleration, double rotationalAcceleration, Stack<Double> turning) {
		super();
		this.translationalAcceleration = translationalAcceleration;
		this.angularAcceleration = rotationalAcceleration;
		this.stack = turning;
		this.finished = false;
	}
	
	public void setStack(Stack<Double> turn){
		this.stack = turn;
	}
	public Stack<Double> getStack(){
		return this.stack;
	}
	
	@Override
	public Movement getMovement(Toroidal2DPhysics space, Ship ship) {
		Movement movement = new Movement();
		movement.setAngularAccleration(angularAcceleration);
		movement.setTranslationalAcceleration(translationalAcceleration);
		return movement;
	}
	
	public double getAngularAccel(){
		return this.angularAcceleration;
	}
	
	public void setFinished(){
		this.finished = true;
	}
	@Override
	public boolean isMovementFinished(Toroidal2DPhysics space) {
		if(space.getCurrentTimestep() >= this.timeFinishOffset){
			return true;
		}
		else
			return false;
	}

}
