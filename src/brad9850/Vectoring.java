package brad9850;

import java.awt.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Vectoring methods to get around, as well as helpers
 * 
 * @author Scott Kannawin & Christopher Bradford
 *
 */
public class Vectoring {
	/**
	 * Work in progress turning function Returns a queue of time steps to move
	 * at full angular acceleration to aim at the target Currently is the
	 * equivalent of a slow moving sprinkler
	 * 
	 * @param space
	 * @param target
	 * @param ship
	 * @return
	 */
	@SuppressWarnings("static-access")
	public static Queue<Integer> aimHelp(Toroidal2DPhysics space, AbstractObject target, Ship ship) {
		// use the function for distance covered, solve for t(time steps)
		// (1/2)*d = 2*vi*t + a*t^2

		// t = ((-vi) + sqrt(vi^2 - 4ad)) / 2a
		Queue<Integer> timesteps = new LinkedList<Integer>();

		double vi = ship.getPosition().getAngularVelocity();
		double a = 3.5355339059;
		double angleA = ship.getPosition().getOrientation();

		Vector2D vectorA = new Vector2D();
		vectorA.fromAngle(angleA, 1000);

		// double d = space.findShortestDistanceVector(ship.getPosition(),
		// target.getPosition()).angleBetween(vectorA);

		double compensator = ship.getPosition().getOrientation();
		double compensateTo = angleBetween(space, ship, target);
		double d = compensator - compensateTo;

		// total time steps to get to the position
		double t = (((-(2 * vi)) + Math.sqrt(Math.abs((4 * vi * vi) - 4 * a * (.5 * d)))) / 2 * a);
		t = Math.ceil(t);
		// System.out.println(vi + "\t" + a + "\t" + d + "\t" + t);

		double aaa = adjustTurn(t, d);

		if (t < 38) {
			timesteps.add((int) t);
			timesteps.add((int) t * -1);
			timesteps.add(0);
		} else {
			timesteps.add((int) t * -1);
			timesteps.add((int) t);
			timesteps.add(0);
		}

		return timesteps;
	}

	public static double adjustTurn(double t, double d) {
		double alphaT = Math.ceil(Math.sqrt(d / 3.5355339059));
		double a = d / (alphaT * alphaT);
		// System.out.println(a);
		return a;
	}

	/**
	 * The vectoring agent behind the advanced movement method, returns a
	 * movement action that will go in the direction you want, towards the
	 * target, quickly
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @param velocity
	 * @return
	 */
	@SuppressWarnings("static-access") // Because Vector2D().fromAngle() cannot
										// be accessed in a static way
	public static AbstractAction nextVector(Toroidal2DPhysics space, Ship ship, AbstractObject target,
			double velocity) {
		// target self if can't resolve a target
		Vector2D direction = null;
		if (target == null) {
			target = ship;
		} else
			direction = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());

		Vector2D gotoPlace = new Vector2D();
		// use that angle for which it is going to accelerate, and set the
		// magnitude up
		if (target != ship)
			gotoPlace = gotoPlace.fromAngle(direction.getAngle(), velocity);
		else
			gotoPlace = new Vector2D(ship.getPosition());

		double compensator = ship.getPosition().getOrientation();
		double compensateTo = angleBetween(space, ship, target);
		double compensate = compensator - compensateTo + 2 * Math.PI;
		gotoPlace.rotate(compensate);

		// set the ship in motion
		AbstractAction sendOff = new MoveAction(space, ship.getPosition(), target.getPosition(), gotoPlace);
		return sendOff;
	}

	/**
	 * Advanced Movement Vector, slows down near the target if it shoot-able
	 * else it will get the right angle and finish movement Has a helper method
	 * below
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @param distanceFactor
	 * @return
	 */
	public static AbstractAction advancedMovementVector(Toroidal2DPhysics space, Ship ship, AbstractObject target,
			int distanceFactor) {
		// speed adjustments relative to max accel
		double movementFactor = 1.6;
		double movementMax = Movement.MAX_TRANSLATIONAL_ACCELERATION * movementFactor;

		AbstractAction sendOff = null;
		double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());

		// gets a set of non shootable asteroids
		Set<AbstractObject> asteroids = new HashSet<AbstractObject>();
		for (Asteroid obj : space.getAsteroids()) {
			if (!obj.isMineable()) {
				asteroids.add(obj);
			}
		}

		// will slow down if within the bounds of the distance, or it won't slow
		// down
		if (distance < distanceFactor) {
			double adjustedVelocity = (distance / distanceFactor) * (movementMax / (movementFactor * 1.25));

			if (target.getClass() == Beacon.class
					&& (Combat.willHitMovingTarget(space, ship, target, target.getPosition().getTranslationalVelocity())
							|| ship.getPosition().getTotalTranslationalVelocity() < movementMax * .1)) {

				sendOff = nextVector(space, ship, target, movementMax);
			} else {
				// TODO make a quick rotate and rotation compensator action
				// method for this
				sendOff = nextVector(space, ship, target, adjustedVelocity);
			}
		}
		// if path is clear it will go
		else if (space.isPathClearOfObstructions(ship.getPosition(), target.getPosition(), asteroids, 0)) {
			sendOff = nextVector(space, ship, target, movementMax);
		}

		// else it will find a new target
		else {
			sendOff = nextVector(space, ship, nextFreeVector(space, ship, target), movementMax);
		}

		return sendOff;
	}

	/**
	 * Helper function of the advanced vectoring function, it finds the next
	 * closest free object with a clear path of the same type that the original
	 * target was on
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	private static AbstractObject nextFreeVector(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		Set<AbstractObject> objSet = space.getAllObjects();
		ArrayList<AbstractObject> targetObjs = new ArrayList<AbstractObject>();

		double minDistance = Double.MAX_VALUE;
		AbstractObject gotoTarget = null;

		// get objects of the same type
		// TODO Adjust for shooting stuff, gathering resources, or getting
		// beacons
		for (AbstractObject obj : objSet) {
			if (obj.getClass() == target.getClass()) {
				targetObjs.add(obj);
			}
		}
		// collects all the asteroids you can't fly through
		Set<AbstractObject> nonShootable = new HashSet<AbstractObject>();
		for (Asteroid asteroid : space.getAsteroids()) {
			if (!asteroid.isMineable()) {
				nonShootable.add(asteroid);
			}
		}
		// adds bases as impassable objects too
		for (Base bases : space.getBases()) {
			nonShootable.add(bases);
		}

		// finds the shortest free path
		for (AbstractObject obj : targetObjs) {
			double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());
			if (distance < minDistance
					&& space.isPathClearOfObstructions(ship.getPosition(), obj.getPosition(), nonShootable, 2)) {
				minDistance = distance;
				gotoTarget = obj;
			}
		}

		return gotoTarget;
	}

	/**
	 * Gets the angle between two objects
	 * 
	 * @param space
	 * @param ship
	 * @param target
	 * @return
	 */
	public static double angleBetween(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		Vector2D pos1 = new Vector2D(ship.getPosition());
		Vector2D pos2 = new Vector2D(target.getPosition());

		double angle = pos1.angleBetween(pos2);

		return angle;
	}

}
