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
		LineGraphics line = new LineGraphics(new Position(x0, y0), new Position(x1, y1), 
				space.findShortestDistanceVector(new Position(x0, y0), new Position(x1, y1)));
		line.setLineColor(color);
		graphicsToAdd.add(line);
	}
}
