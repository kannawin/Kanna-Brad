package kann0200;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;

import kann0200.GAChromosome;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;

/**
 * Stores a whole population of individuals for genetic algorithms / evolutionary computation
 * 
 * @author amy
 *
 */
public class GAPopulation {
	private GAChromosome[] population;
	private String filename;
	
	private int populationNumber;
	private int currentPopulationCounter;
	private double velocity;
	private double[] lastGenVelocity;
	private double[] currentGenVelocity;
	private double[] fitnessScores;
	private double[] velocityHolder;
	private double[] scores;
	/**
	 * Make a new empty population
	 */
	public GAPopulation(int populationSize) {
		super();
		
		// start at member zero
		currentPopulationCounter = 0;
		
		// make an empty population
		population = new GAChromosome[populationSize];
		
		for (int i = 0; i < populationSize; i++) {
			population[i] = new GAChromosome();
		}
		
		// make space for the fitness scores
		fitnessScores = new double[populationSize];
		scores = new double[populationSize];
		lastGenVelocity = new double[populationSize];
		currentGenVelocity = new double[populationSize];
		velocityHolder = new double[populationSize];
	}
	public void setGeneration(String file){
		this.filename = file;
		BufferedReader reader;
		try{
			File fileInstantiator = new File(file);
			fileInstantiator.createNewFile(); 
			reader = new BufferedReader(new FileReader(file));
			String temp;
			if(reader.readLine() == null){
				//if nothing exists, max it out
				this.velocity = Movement.MAX_TRANSLATIONAL_ACCELERATION * 5;
				this.populationNumber = 0;
			}
			else{
				reader.reset();
				while((temp = reader.readLine()) != null){
					this.velocity = Double.valueOf(parseVelocity(temp));
					this.populationNumber++;
				}
			}
		}
		//simple catch and release, because the file is guaranteed to exist, else itll get created
		catch(FileNotFoundException e){
		}
		catch(IOException e){
		}
	}
	
	private double parseVelocity(String input){
		String[] temp = input.split(",");
		double temp1 = 0.0;
		for(int i = 0; i < temp.length;i++){
			temp1 = Double.valueOf(temp[i]);
		}
		return temp1;
	}
	
	public double getVelocity(){
		return this.velocity;
	}
	public int getGeneration(){
		return this.currentPopulationCounter;
	}
	/**
	 * Currently scores all members as zero (the student must implement this!)
	 * 
	 * @param space
	 */
	public void evaluateFitnessForCurrentMember(Toroidal2DPhysics space, Ship ship, int deltaDeath, int deltaKill, double speed) {
		
		//everything is positive, for mathematical reasons
		int tempScore = 0;
		if(ship.getEnergy() < 500){
			if(ship.getEnergy() < 100)
				tempScore += 1;
			else
				tempScore += 2;
		}
		else
			tempScore += 15;
		tempScore += (ship.getKillsInflicted() - deltaKill) * 6;
		tempScore += (ship.getKillsReceived() - deltaDeath) * 1;
		
		fitnessScores[currentPopulationCounter] = tempScore;
		currentGenVelocity[currentPopulationCounter] = speed;
		scores[currentPopulationCounter] = (ship.getDamageInflicted() - ship.getDamageReceived());

		try {
			FileWriter fw = new FileWriter(this.filename, true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);
			out.print(String.valueOf(this.velocity) + "/" + String.valueOf(tempScore) + "/" + String.valueOf(this.scores[currentPopulationCounter]) + ",");
			out.close();
		} catch (IOException e) {
		}
		
		
	}

	/**
	 * Return true if we have reached the end of this generation and false otherwise
	 * 
	 * @return
	 */
	public boolean isGenerationFinished() {
		if (currentPopulationCounter == population.length) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Return the next member of the population (handles wrapping around by going
	 * back to the start but then the assumption is that you will reset with crossover/selection/mutation
	 * 
	 * @return
	 */
	public void getNextMember() {
		currentPopulationCounter++;
		
		//return population[currentPopulationCounter % population.length];
	}
	
	private double avgVelocity(){
		double temp = 0.0;
		for(int i = 0; i < this.lastGenVelocity.length;i++){
			temp += lastGenVelocity[i];
		}
		return temp;
	}
	
	
	/**
	 * Does crossover, selection, and mutation using our current population.
	 * Note, none of this is implemented as it is up to the student to implement it.
	 * Right now all it does is reset the counter to the start.
	 */
	public void makeNextGeneration() {
		currentPopulationCounter = 0;
		lastGenVelocity = currentGenVelocity;
		int scoreCount = 0;
		ArrayList<Integer> temp = new ArrayList<Integer>();
		
		try {
			FileWriter fw = new FileWriter(this.filename, true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);
			out.print(String.valueOf(avgVelocity()) + "\n");
			out.close();
		} catch (IOException e) {
		}
		
		for(int i = 0; i < this.fitnessScores.length;i++){
			scoreCount +=(int) fitnessScores[i];
			for(int j = 0; j<(int)fitnessScores[i];j++){
				temp.add(i);
			}
		}
		int[] indices = new int[scoreCount];
		for(int i = 0; i < temp.size();i++){
			indices[i] = temp.get(i);
		}
		for(int i = 0; i < this.velocityHolder.length;i++){
			Random r = new Random();
			double p1 = lastGenVelocity[indices[r.nextInt(velocityHolder.length)]];
			double p2 = lastGenVelocity[indices[r.nextInt(velocityHolder.length)]];
			velocityHolder[i] = (p1 + p2)/2;
		}
	}
	
	public double nextVelocity(){
		if(this.populationNumber != 0){
			return this.velocityHolder[this.currentPopulationCounter];
		}
		else{
			Random r = new Random();
			double velocity = Movement.MAX_TRANSLATIONAL_ACCELERATION*4*r.nextDouble();
			this.currentGenVelocity[this.currentPopulationCounter] = velocity;
			return velocity;
		}
		
	}
	
	/**
	 * Return the first member of the popualtion
	 * @return
	 */
	public GAChromosome getFirstMember() {
		return population[0];
	}
}
	

