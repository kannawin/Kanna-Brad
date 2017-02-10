package spacesettlers.gui;

import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;

/**
 * Displays the debug info in a nice gridded format for an object
 * 
 * @author Chris
 */
public class DebugPanel extends JPanel {
	JLabel positionX, positionY, velocityX, velocityY, orientationValue, energyCurrent, energyMax;

	public DebugPanel() {
		setLayout(new GridLayout(5,3, 4, 1));
		
		// row 1: the titles
		JLabel empty = new JLabel("");
		add(empty);

		JLabel x = new JLabel("X");
		add(x);

		JLabel y = new JLabel("Y");
		add(y);
		
		// the data: next row is position
		JLabel position = new JLabel("Pos: ");
		add(position);
		
		positionX = new JLabel("0");
		add(positionX);
		
		positionY = new JLabel("0");
		add(positionY);

		//Velocity Row
		JLabel velocity = new JLabel("Vel: ");
		add(velocity);

		velocityX = new JLabel("0");
		add(velocityX);
		
		velocityY = new JLabel("0");
		add(velocityY);
		
		//Orientation row
		JLabel orientation = new JLabel("Ori: ");
		add(orientation);

		orientationValue = new JLabel("0");
		add(orientationValue);
		
		JLabel orientationEmpty = new JLabel(" ");
		add(orientationEmpty);

		//Energy Row
		JLabel energy = new JLabel("NRG: ");
		add(energy);

		energyCurrent = new JLabel("0");
		add(energyCurrent);
		
		energyMax = new JLabel("0");
		add(energyMax);
	}

	public void updateData(AbstractObject object) {
		
		if (object != null) {
			positionX.setText("" + round(object.getPosition().getX(), 1));
			positionY.setText("" + round(object.getPosition().getY(), 1));
			velocityX.setText("" + round(object.getPosition().getxVelocity(), 1));
			velocityY.setText("" + round(object.getPosition().getyVelocity(), 1));
			orientationValue.setText("" + round(object.getPosition().getOrientation(), 1));
			
			if(object instanceof AbstractActionableObject){
				AbstractActionableObject ship = (AbstractActionableObject) object;
				energyCurrent.setText("" + ship.getEnergy());
				energyMax.setText("" + ship.getMaxEnergy());
			}
			else{
				energyCurrent.setText("0");
				energyMax.setText("0");
			}
		}		
	}
	
	private double round(double decimal, int places){
		for(int i = 0; i < places; i++){
			decimal *= 10;
		}
		decimal = Math.round(decimal);
		for (int i = 0; i < places; i++) {
			decimal /= 10;
		}
		return decimal;
	}

	
}
