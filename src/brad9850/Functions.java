package brad9850;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import brad9850.DrawFunctions;

public class Functions{
	
	/**
	 * See if a team is a non-AI enemy
	 * @param teamName The name of the team we are checking
	 * @param friendlyTeamName The name of our team
	 */
	public static boolean isHumanEnemyTeam(String teamName, String friendlyTeamName){
		//See if it's our name
		if(teamName.equalsIgnoreCase(friendlyTeamName)){
			return false;
		}

		String[] aiNames = {"RandomTeam", "DoNothingTeam", "HeuristicTeam"};
		//See if it's an AI name
		for(String name: aiNames){
			if(teamName.equalsIgnoreCase(name)){
				return false;
			}
		}
		
		//Otherwise, it's a human enemy
		return true;
	}
	
	/**
	 * See if a ship is pointed at at target, so that if it shoots it'll hit it
	 * Only wraps torus once
	 * @param ship
	 * @param target
	 * @return
	 */
	public static boolean isAimingAtTarget(Toroidal2DPhysics space, Ship ship, AbstractObject target){
		double shipX = ship.getPosition().getX();
		double shipY = ship.getPosition().getY();
		
		System.out.println(target.getPosition());
		
		//Position's orientation is organized as follows: 
		//	Top is negative, going from -Pi (left side) to 0 (right side).
		//	Bottom is positive, also going from Pi (left side) to 0 (right side)
		//	Units are radians
		double shipOrientation = ship.getPosition().getOrientation();
		//Reverse orientation since positive y is down, not up
		//shipOrientation = -shipOrientation;
		
//		if(shipOrientation < 0){
//			shipOrientation = Math.PI + shipOrientation;
//		}
//		//Convert orientation into traditional form, where the far right is 0, and it increases going counter-clockwise until 2*Pi
//		//Flip the direction of the bottom half of the orientation circle, so that the whole thing goes counter clockwise
//		if(shipOrientation > 0){
//			shipOrientation = Math.PI - shipOrientation;
//		}
//		//Add Pi so that the circle goes from 0 to 2*Pi instead of -Pi to Pi
//		shipOrientation += Math.PI;
//		
//		if(shipOrientation < Math.PI){
//			shipOrientation = Math.PI - shipOrientation;
//		}
		
		//Using the distance function here: http://math.stackexchange.com/questions/275529/check-if-line-intersects-with-circles-perimeter
		double a = Math.tan(shipOrientation);
		double b = -1;
		double c = shipY - a * shipX;
		
		double targetX = target.getPosition().getX();
		double targetY = target.getPosition().getY();
		
		//System.out.println(shipOrientation);
		//Adjust for toroidal math
		//Ship is facing down & target is above ship, move target down a screen
		if(shipOrientation > 0 && targetY < shipY){
			targetY += space.getHeight();
		}
		//Ship is facing up & target is below ship, move target up a screen
		if(shipOrientation < 0 && targetY > shipY){
			targetY -= space.getHeight();
		}
		//Ship is facing right & target is to the left, move target right a screen
		if(Math.abs(shipOrientation) < Math.PI / 2 && targetX < shipX){
			targetX += space.getWidth();
		}
		//Ship is facing left & target is to the right of ship, move target left a screen
		if(Math.abs(shipOrientation) > Math.PI / 2 && targetX > shipX){
			targetX -= space.getWidth();
		}
		
		double distanceToTargetCenter = Math.abs(a * targetX + b * targetY + c) / Math.sqrt(a*a + b*b);
		
		if(distanceToTargetCenter <= target.getRadius() + Missile.MISSILE_RADIUS){
			return true;
		}
		else{
			return false;
		}
	} 
	
	
}