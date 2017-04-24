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
 * 
 * The genetic algorithm population and evaluation functions are stored here, it attempts to find the ideal
 * velocity to input into the advanced movement function to the targets give from the GA State. 
 * 
 * The chromosomes are also maintained here by storing the current velocity for the last generation
 * it also prints out the results to each item in the population
 * 
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
		Random r = new Random();
		this.velocity = (double) r.nextInt((int)Movement.MAX_TRANSLATIONAL_ACCELERATION * 4);
		// make space for the fitness scores
		fitnessScores = new double[populationSize];
		scores = new double[populationSize];
		lastGenVelocity = new double[populationSize];
		currentGenVelocity = new double[populationSize];
		velocityHolder = new double[populationSize];
	}
	
	/**
	 * Instantiates the generation from game to game, using the file printed out beforehand, if no file
	 * is found it starts the algorithm over again and sets each velocity to random doubles
	 * 
	 * @param file
	 */
	public void setGeneration(String file){
		this.filename = file;
		BufferedReader reader;
		try{
			File fileInstantiator = new File(file);
			fileInstantiator.createNewFile(); 
			reader = new BufferedReader(new FileReader(file));
			String temp;
			temp = reader.readLine();
			this.velocity = parseVelocity(temp);

			System.out.println(this.currentPopulationCounter);
		}
		//simple catch and release, because the file is guaranteed to exist, else itll get created
		catch(FileNotFoundException e){
		}
		catch(IOException e){
		}
	}
	
	/**
	 * Used to help read the file and parse the given data, it sets the current population number
	 * it also sets the generation number and returns the last known velocity
	 * 
	 * @param input
	 * @return
	 */
	private double parseVelocity(String input){
		try{
			int gen = input.split("||").length;
			this.populationNumber = gen;
		}
		catch(NullPointerException e){
			this.populationNumber = 1;
		}
		double temp1 = 50.0;
		try{
			String[] temp = input.split(",");
			this.currentPopulationCounter = temp.length % this.scores.length;
			temp1 = 0.0;
			for(int i = 0; i < temp.length - 1;i++){
				temp1 = Double.valueOf(temp[i].split("/")[0]);
				this.currentGenVelocity[i] = temp1;
			}
		}
		catch(NullPointerException e){
			temp1 = 50.0;
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
	 * The fitness evaluation. Everything is positive so finding the probability is just an simple RNG value
	 * to get probabilistic opportunities for everything
	 * 
	 * it firsts determines if it is low on energy at time of evaluation, <500 is unacceptable
	 * and gives extra points for being above that threshold
	 * 
	 * after energy evaluation is done, points are given for kills, less so for deaths
	 * 
	 * @param space
	 * @param ship
	 * @param deltaDeath
	 * @param deltaKill
	 * @param speed
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
		scores[currentPopulationCounter] = (ship.getDamageInflicted() - ship.getDamageReceived()) + ship.getKillsInflicted()*1000 - ship.getKillsReceived()*(-1000);
		
		try {
			FileWriter fw = new FileWriter(this.filename, true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);
			out.print(this.velocity + "/" + tempScore + "/" + this.scores[currentPopulationCounter] + ",");
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
	/**
	 * evaluates the net velocity used for calculating the average at the end of a generation
	 * 
	 * @return
	 */
	private double avgVelocity(){
		double temp = 0.0;
		for(int i = 0; i < this.lastGenVelocity.length;i++){
			temp += lastGenVelocity[i];
		}
		return temp;
	}
	
	
	/**
	 * this will create the next generation and evaluates the next generations velocity numbers
	 * it will create an array of sum(fitnessScores) length and index appropriately
	 * it will then use a random number generator to crossover two parents (getting their average velocity)
	 * and sets it at a point in the velocity array
	 * 
	 * this method will lead to asexual members in a generation, but will allow for normalization after
	 * some generations, because survival of the fittest
	 * 
	 * 
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
			out.print("||" + avgVelocity() + "|" + "\n");
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
		this.currentGenVelocity = this.velocityHolder;
		this.populationNumber++;
	}
	
	/**
	 * This is where mutation is done
	 * it will randomly generate a new velocity, and is forced into normalization with lower and lower 
	 * variability between generations (modifier) 
	 * 
	 * this will mutate the velocity and force it to generate a random number in the full range (1-200)
	 * if the fully generated number is outside of the range (0 < n < 250) because its either too slow
	 * or too fast
	 * 
	 * @return
	 */
	public double nextVelocity(){
		Random r = new Random();
		try{
			this.velocity = this.currentGenVelocity[this.currentPopulationCounter] * r.nextDouble() * 2;
			if(this.velocity <=1 ){
				this.velocity = r.nextDouble() * Movement.MAX_TRANSLATIONAL_ACCELERATION * 4;
			}
		}
		catch(NullPointerException e){
			this.velocity = r.nextDouble() * Movement.MAX_TRANSLATIONAL_ACCELERATION * 4;
		}
		this.currentGenVelocity[this.currentPopulationCounter] = this.velocity;
		double modifier = (Math.pow(.95, this.populationNumber));
		modifier = r.nextBoolean() ? modifier * -1 : modifier;
		this.velocity += this.velocity * modifier;
		if(this.velocity <= 0 && this.populationNumber > 5 || this.velocity > 250){
			this.velocity = r.nextDouble() * Movement.MAX_TRANSLATIONAL_ACCELERATION * 4;
		}
		
		
		System.out.println(this.velocity);
		this.currentGenVelocity[this.currentPopulationCounter] = velocity;
		return velocity;	
	}
	
	/**
	 * Return the first member of the popualtion
	 * @return
	 */
	public GAChromosome getFirstMember() {
		return population[0];
	}
}
	

