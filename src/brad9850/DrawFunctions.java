package brad9850;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;

import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

public class DrawFunctions {
	private static ArrayList<SpacewarGraphics> graphicsToAdd = new ArrayList<SpacewarGraphics>();
	
	
	public static Graphics2D graphics;
	public static Color color = Color.RED;
	
	public static void Refresh(){
		graphicsToAdd.clear();
	}
	
	public static ArrayList<SpacewarGraphics> GetGraphics(){
		return graphicsToAdd;
	}
	
	public static void DrawLine(Toroidal2DPhysics space, int x0, int y0, int x1, int y1){
		DrawLine(space, x0, y0, x1, y1, true);
	}
	
	public static void DrawLine(Toroidal2DPhysics space, int x0, int y0, int x1, int y1, boolean wrap){
		Position start = new Position(x0, y0);
		Position end = new Position(x1, y1);
		Vector2D line;
		if(wrap){
			line = space.findShortestDistanceVector(start, end);
		}
		else{
			line = new Vector2D(x1 - x0, y1 - y0);
		}
		
		LineGraphics drawLine = new LineGraphics(start, end, line);
		drawLine.setLineColor(color);
		graphicsToAdd.add(drawLine);
	}
}
