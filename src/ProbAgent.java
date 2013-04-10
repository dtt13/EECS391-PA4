/**
 *  Strategy Engine for Programming Intelligent Agents (SEPIA)
    Copyright (C) 2012 Case Western Reserve University

    This file is part of SEPIA.

    SEPIA is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SEPIA is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.
 */
//package edu.cwru.sepia.agent;


import java.awt.Point;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.ProductionAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template.TemplateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;

/**
 * @author Derrick Tilsner
 * @author Sam Fleckenstein
 *
 */
public class ProbAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(ProbAgent.class.getCanonicalName());
	private static final double APPROX_TOWER_DENSITY = 0.0085;
	private static final double MAX_EXPLORE_FACTOR = .3;

	private int boardSizeRow;
	private int boardSizeColumn;
	private boolean hasSeen[][];
	private double towerProb[][];
	private int numVisits[][];
	private int numHits[][];
	private boolean seenGold = false;
	private Point goldLoc = new Point();
	private Integer goldId;
	
	private boolean producedPeasant;
	
	private PreviousState prevState;
	
	StateView currentState;
	private int step;
	private ArrayList<Integer> peasantIds;
	private ArrayList<Integer> townhallIds;
	
	private Direction directions[] = new Direction[8];
	private double avgBoardSize;
	private double exploreCoeff;

	public ProbAgent(int playernum, String[] arguments) {
		super(playernum);
		
		boardSizeRow = 0;
		boardSizeColumn = 0;
		
		producedPeasant = false;
		
		directions[0] = Direction.NORTHEAST;
		directions[1] = Direction.SOUTHEAST;
		directions[2] = Direction.SOUTHWEST;
		directions[3] = Direction.NORTHWEST;
		directions[4] = Direction.NORTH;
		directions[5] = Direction.EAST;
		directions[6] = Direction.SOUTH;
		directions[7] = Direction.WEST;
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newState, History.HistoryView statehistory) {
		step = 0;
		currentState = newState;
		
		boardSizeColumn = currentState.getXExtent();
		boardSizeRow = currentState.getYExtent();
		
		avgBoardSize = (boardSizeColumn + boardSizeRow);
		exploreCoeff = MAX_EXPLORE_FACTOR * (2 / avgBoardSize) * (2 / avgBoardSize);
		
		goldLoc.x = boardSizeColumn - 5;
		goldLoc.y = 2;
		
		hasSeen = new boolean[boardSizeColumn][boardSizeRow];
		towerProb = new double[boardSizeColumn][boardSizeRow];
		numVisits = new int[boardSizeColumn][boardSizeRow];
		numHits = new int[boardSizeColumn][boardSizeRow];
		
		for(int i = 0; i < boardSizeColumn; i++) {
			for(int j = 0; j < boardSizeRow; j++) {
				hasSeen[i][j] = false;
				towerProb[i][j] = APPROX_TOWER_DENSITY;
				numVisits[i][j] = 0;
				numHits[i][j] = 0;
			}
		}
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		HashMap<Integer, Integer> peasantHP = new HashMap<Integer, Integer>();
		HashMap<Integer, Point> peasantLoc = new HashMap<Integer, Point>();
		peasantIds = new ArrayList<Integer>();
		townhallIds = new ArrayList<Integer>();
		for(int i = 0; i < allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			String unitTypeName = unit.getTemplateView().getName();			
			if(unitTypeName.equals("TownHall")) {
				townhallIds.add(id);
			}
			if(unitTypeName.equals("Peasant")) {
				peasantIds.add(id);
				peasantHP.put(id, unit.getHP());
				Point unitLoc = new Point();
				unitLoc.x = unit.getXPosition();
				unitLoc.y = unit.getYPosition();
				peasantLoc.put(id, unitLoc);
			}
		}
		
		prevState = new PreviousState(peasantIds, peasantHP, peasantLoc);
		
		return middleStep(newState, statehistory);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		currentState = newState;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}
		Map<Integer, Action> builder = new HashMap<Integer, Action>();
		
		//ANALYZE PHASE
		
		prevState.removeMarked();
		
		if(producedPeasant) {
			List<Integer> allUnitIds = currentState.getAllUnitIds();
			peasantIds = new ArrayList<Integer>();
			for(int i = 0; i < allUnitIds.size(); i++) {
				int id = allUnitIds.get(i);
				UnitView unit = currentState.getUnit(id);
				String unitTypeName = unit.getTemplateView().getName();		
				if(unitTypeName.equals("Peasant")) {
					peasantIds.add(id);
				}
			}
			int peasantID = peasantIds.get(peasantIds.size() - 1);
			UnitView peasant = currentState.getUnit(peasantID);
			prevState.addPeasant(peasantID, peasant.getHP(), new Point(peasant.getXPosition(), peasant.getYPosition()));
			producedPeasant = false;
		}
		
		for(int peasantID : prevState.getPeasantIds()) {
			if(!currentState.getUnitIds(0).contains(peasantID)) {		//peasant dies
				Point peasantLoc = prevState.getPeasantLoc(peasantID);
				numHits[peasantLoc.x][peasantLoc.y]++;
				numVisits[peasantLoc.x][peasantLoc.y]++;
				updateTowerProbs(true, peasantLoc.x, peasantLoc.y);
				
				List<Integer> allUnitIds = currentState.getAllUnitIds();
				peasantIds = new ArrayList<Integer>();
				for(int i = 0; i < allUnitIds.size(); i++) {
					int id = allUnitIds.get(i);
					UnitView unit = currentState.getUnit(id);
					String unitTypeName = unit.getTemplateView().getName();		
					if(unitTypeName.equals("Peasant")) {
						peasantIds.add(id);
					}
				}
				prevState.markForRemoval(peasantID);
				continue;
			}
			
			UnitView peasant = currentState.getUnit(peasantID);
			int peasantX = peasant.getXPosition();
			int peasantY = peasant.getYPosition();
			setSeenLocations(peasantID, peasantX, peasantY);
			numVisits[peasantX][peasantY]++;
			if(prevState.getPeasantHP(peasantID) > peasant.getHP()) { //got hit
				numHits[peasantX][peasantY]++;
				prevState.setPeasantHP(peasantID, peasant.getHP());
				updateTowerProbs(true, peasantX, peasantY);
			} else { //didn't get hit
				updateTowerProbs(false, peasantX, peasantY);
			}
		}	

		//you failed
		if(peasantIds.size() == 0) {
			terminalStep(currentState, statehistory);
		}
		int peasantID = peasantIds.get(0);
		UnitView peasant = currentState.getUnit(peasantID);
		//DECIDE MOVE PHASE
		Action b = null;
		
		//townhall actions
		if(peasantIds.size() == 1 && currentState.getResourceAmount(0, ResourceType.GOLD) >= 400) {
			TemplateView peasantTemplate = currentState.getTemplate(0, "Peasant");
			int peasantTemplateId = peasantTemplate.getID();
			b = new ProductionAction(townhallIds.get(0), ActionType.COMPOUNDPRODUCE, peasantTemplateId);
			builder.put(townhallIds.get(0), b);
			producedPeasant = true;
		}
		
		//peasant actions
		if(seenGold && peasant.getCargoAmount() == 0 && adjacentToGold(peasant)) { //adjacent to gold and has nothing in hand, gather
			b = new TargetedAction(peasantID, ActionType.COMPOUNDGATHER, goldId);
		} else if(peasant.getCargoAmount() != 0 && adjacentToTownhall(peasant)) { //adjacent to townhall and has something in hand, deposit
			b = new TargetedAction(peasantID, ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
		} else { //move somewhere
			Direction toMove = findNextMove(peasantID);
			b = new DirectedAction(peasantID, ActionType.PRIMITIVEMOVE, toMove);
		}
		
		builder.put(peasantID, b);
//		printTowerProbs();

		//EXECUTE MOVE PHASE
		
		return builder;
	}

	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}

		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Congratulations! You have finished the task!");
		} else {
			logger.fine("Congratualations! All your peasants died!");
			System.exit(0);
		}
	}

	/**
	 * 
	 * @param peasant - The peasant you are concerned with
	 * @return True if the peasant is next to the townhall
	 */
	private boolean adjacentToTownhall(UnitView peasant) {
		UnitView townhall = currentState.getUnit(townhallIds.get(0));
		Point townhallLoc = new Point();
		townhallLoc.x = townhall.getXPosition();
		townhallLoc.y = townhall.getYPosition();
		
		Point peasantLoc = new Point();
		peasantLoc.x = peasant.getXPosition();
		peasantLoc.y = peasant.getYPosition();
		
		if(Math.abs(townhallLoc.x - peasantLoc.x) <= 1
				&& Math.abs(townhallLoc.y - peasantLoc.y) <= 1) {
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @param peasant - The peasant you are concerned with
	 * @return True if the peasant is next to the gold
	 */
	private boolean adjacentToGold(UnitView peasant) {
		Point peasantLoc = new Point();
		peasantLoc.x = peasant.getXPosition();
		peasantLoc.y = peasant.getYPosition();
		if(Math.abs(peasantLoc.x - goldLoc.x) <= 1
				&& Math.abs(peasantLoc.y - goldLoc.y) <= 1) {
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param x - x peasant location
	 * @param y - y peasant location
	 */
	private void setSeenLocations(int peasantId, int x, int y) {
		//sets every location within range of sight to true
		Point seen = new Point();
		for(int i = -2; i <= 2; i++) {
			for(int j = -2; j <= 2; j++) {
				seen.x = x + i;
				seen.y = y + j;
				
				if(currentState.inBounds(seen.x, seen.y)) {
					hasSeen[seen.x][seen.y] = true;
					Integer unitID = currentState.unitAt(seen.x, seen.y);
					
					if(currentState.isResourceAt(seen.x, seen.y)) {
						Integer resource = currentState.resourceAt(seen.x, seen.y);
						if(currentState.getResourceNode(resource).getType().toString().equals("GOLD_MINE")) {
							seenGold = true;
							goldLoc = new Point(seen.x, seen.y);
							goldId = resource;
						}
						towerProb[seen.x][seen.y] = 0.0;
					} else if(unitID != null 
							&& unitID != townhallIds.get(0)
							&& !peasantIds.contains(unitID)) {
						towerProb[seen.x][seen.y] = 1.0;
					} else {
						towerProb[seen.x][seen.y] = 0.0;
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * @param peasantID - The ID of the peasant you are concerned with
	 * @return The direction with the least probability of getting hit
	 */
	private Direction findNextMove(int peasantID) {
		double minProb = 9999999;
		Direction dirToMove = null;
		
		UnitView peasant = currentState.getUnit(peasantID);
		int currentX = peasant.getXPosition();
		int currentY = peasant.getYPosition();
		
		int newX = currentX;
		int newY = currentY;
		
		int deltaX = 0;
		int deltaY = 0;
		for(Direction dir : directions) { 	
			switch(dir) {
			case NORTH:
				deltaX = 0;
				deltaY = -1;
				break;
			case NORTHEAST:
				deltaX = 1;
				deltaY = -1;
				break;
			case EAST:
				deltaX = 1;
				deltaY = 0;
				break;
			case SOUTHEAST:
				deltaX = 1;
				deltaY = 1;
				break;
			case SOUTH:
				deltaX = 0;
				deltaY = 1;
				break;
			case SOUTHWEST:
				deltaX = -1;
				deltaY = 1;
				break;
			case WEST:
				deltaX = -1;
				deltaY = 0;
				break;
			case NORTHWEST:
				deltaX = -1;
				deltaY = -1;
				break;
			}
			
			boolean hasCargo = peasant.getCargoAmount() > 0;
			if(!currentState.inBounds(currentX + deltaX, currentY + deltaY)
					|| currentState.isResourceAt(currentX + deltaX, currentY + deltaY)
					|| currentState.isUnitAt(currentX + deltaX, currentY + deltaY)) {
				continue;
			}
			
			double currentProb = probOfGettingHit(currentX + deltaX, currentY + deltaY) 
					+ objectiveFunction(peasantID, !hasCargo, currentX, currentY, currentX + deltaX, currentY + deltaY);
			if(currentProb < minProb) {
				minProb = currentProb;
				dirToMove = dir;
				newX = currentX + deltaX;
 				newY = currentY + deltaY;
			}
		}
		prevState.setPeasantLoc(peasantID, newX, newY);
		return dirToMove;
	}

	/**
	 * 
	 * @param toGold - True if the peasant is moving towards the gold
	 * @param currentX - The peasant's current x coordinate
	 * @param currentY - The peasant's current y coordinate
	 * @param nextX - The potential next x coordinate
	 * @param nextY - The potential next y coordinate
	 * @return Factors in exploration, rather than just safety of next moves
	 */
	private double objectiveFunction(int peasantId, boolean toGold, int currentX, int currentY, int nextX, int nextY) {
		double objectiveValue = 0;
		int currentDistance = 0;
		int nextDistance = 0;
		double xProbs = 0;
		double yProbs = 0;
		
		double reward = 0.5;
		if(toGold) {
			currentDistance = Math.abs(goldLoc.x - currentX) + Math.abs(goldLoc.y - currentY);
			nextDistance = Math.abs(goldLoc.x - nextX) + Math.abs(goldLoc.y - nextY);
			
			//This next section is calculating the probability density of
			//the spaces that are in the direction of the goal.
			//The agent is then more likely to move towards the one with the lower density.
			double leftProb = 0;
			double rightProb = 0;
			double aboveProb = 0;
			double belowProb = 0;
			int leftSquares = 0;
			int rightSquares = 0;
			int aboveSquares = 0;
			int belowSquares = 0;
			for(int i = 0; i < boardSizeColumn; i++) {
				for(int j = 0; j < boardSizeRow; j++) {
					if(currentX < i) {
						leftProb += towerProb[i][j];
						leftSquares++;
					} else {
						rightProb += towerProb[i][j];
						rightSquares++;
					}
					
					if(currentY < j) {
						belowProb += towerProb[i][j];
						belowSquares++;
					} else {
						aboveProb += towerProb[i][j];
						aboveSquares++;
					}
				}
			}
			leftProb /= (leftSquares + Math.random() * 10);
			rightProb /= (rightSquares + Math.random() * 10);
			aboveProb /= (aboveSquares + Math.random() * 10);
			belowProb /= (belowSquares + Math.random() * 10);
			
			if(leftProb <= rightProb
					&& leftProb <= aboveProb
					&& leftProb <= belowProb) {
				if(nextX < currentX) {
					objectiveValue -= .1;
				} else {
					objectiveValue += .1;
				}
			} else if(rightProb <= leftProb
					&& rightProb <= aboveProb
					&& rightProb <= belowProb) {
				if(nextX > currentX) {
					objectiveValue -= .1;
				} else {
					objectiveValue += .1;
				}
			} else if(aboveProb <= leftProb
					&& aboveProb <= rightProb
					&& aboveProb <= belowProb) {
				if(nextY < currentY) {
					objectiveValue -= .1;
				} else {
					objectiveValue += .1;
				}
			} else if(belowProb <= leftProb
					&& belowProb <= rightProb
					&& belowProb <= aboveProb) {
				if(nextY > currentY) {
					objectiveValue -= .1;
				} else {
					objectiveValue += .1;
				}
			}
			
			for(int i = currentX; i < goldLoc.x; i++) {
				for(int j = 0; j < boardSizeRow; j++) {
					xProbs += towerProb[i][j];
				}
			}
			
			xProbs /= (Math.abs(currentX - goldLoc.x) * boardSizeRow);

			for(int i = 0; i < boardSizeColumn; i++) {
				for(int j = goldLoc.y; j < currentY; j++) {
					yProbs += towerProb[i][j];
				}
			}
			
			yProbs /= (boardSizeColumn * Math.abs(goldLoc.y - currentY));
			
			if(currentX < nextX) {
				if(xProbs < yProbs) {
					objectiveValue -= reward / nextDistance;
				} else {
					objectiveValue += reward / nextDistance;
				}
			}
			if(currentY > nextY) {
				if(yProbs < xProbs) {
					objectiveValue -= reward;
				} else {
					objectiveValue += reward;
				}
			}
		} else {
			UnitView townhall = currentState.getUnit(townhallIds.get(0));
			currentDistance = Math.abs(townhall.getXPosition() - currentX) + Math.abs(townhall.getYPosition() - currentY);
			nextDistance = Math.abs(townhall.getXPosition() - nextX) +  Math.abs(townhall.getYPosition() - nextY);
		
			for(int i = townhall.getXPosition(); i < currentX; i++) {
				for(int j = 0; j < boardSizeRow; j++) {
					xProbs += towerProb[i][j];
				}
			}
			xProbs /= (Math.abs(townhall.getXPosition() - currentX) * boardSizeRow);
			
			for(int i = 0; i < boardSizeColumn; i++) {
				for(int j = currentY; j < townhall.getYPosition(); j++) {
					yProbs += towerProb[i][j];
				}
			}
			yProbs /= (boardSizeColumn * Math.abs(currentY - townhall.getYPosition()));
			
			if(nextX < currentX) {
				if(xProbs < yProbs) {
					objectiveValue -= reward;
				} else {
					objectiveValue += reward;
				}
			}
			
			if(nextY > currentY) {
				if(yProbs < xProbs) {
					objectiveValue -= reward;
				} else {
					objectiveValue += reward;
				}
			}
 		}
		
		if(nextDistance < currentDistance) {
			objectiveValue -= 0.4 * (currentDistance - nextDistance);
		}
		if(currentDistance < 7) {
			objectiveValue -= 0.2 * (currentDistance - nextDistance);
		}
		
		double exploreFactor = -exploreCoeff * currentDistance * (currentDistance - avgBoardSize);// * (-.5 * numVisits[nextX][nextY] + 1);

		return objectiveValue - exploreFactor;
	}
	
	/**
	 * Sums the probabilities there being a tower at
	 * all of the locations that are in range of the
	 * peasantLoc.
	 * @param x - Potential peasant location x coordinate
	 * @param y - Potential peasant location y coordinate
	 * @return Probability of getting hit at (x, y)
	 */
	private double probOfGettingHit(int x, int y) {
		double totalProb = 0;
		
		Point tower = new Point();
		for(int i = -4; i <= 4; i++) {
			for(int j = -4; j <= 4; j++) {
				tower.x = x + j;
				tower.y = y + i;
				
				if(currentState.inBounds(tower.x, tower.y)) {
					totalProb += towerProb[tower.x][tower.y];
				}
			}
		}
		
		return 0.75 * totalProb;
	}
	
	/**
	 * Updates the probabilities of towers being at various locations
	 * given whether or not the peasant got hit at its current location.
	 * @param gotHit - True if the peasant got hit at the location
	 * @param x - The peasant's x location
	 * @param y - The peasant's y location
	 */
	private void updateTowerProbs(boolean gotHit, int x, int y) {
		double changedSum = 0;
		
		Point tower = new Point();
		if(gotHit) {
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]) {
						towerProb[tower.x][tower.y] *= binomialCoeff(numVisits[x][y], numHits[x][y])
								* Math.pow(0.75, numHits[x][y]) 
								* Math.pow(0.25, numVisits[x][y] - numHits[x][y]);
						if(towerProb[tower.x][tower.y] != 1) {
							changedSum += towerProb[tower.x][tower.y];
						}
					}
				}
			}
			if(changedSum != 0) {
				for(int i = -4; i <= 4; i++) {
					for(int j = -4; j <= 4; j++) {
						tower.x = x + j;
						tower.y = y + i;
						
						if(currentState.inBounds(tower.x, tower.y) 
								&& !hasSeen[tower.x][tower.y]
								&& towerProb[tower.x][tower.y] != 1) {
							if(towerProb[tower.x][tower.y] < .000001) {
								towerProb[tower.x][tower.y] = 0;
							}
							if(towerProb[tower.x][tower.y] > .5) {
								towerProb[tower.x][tower.y] = 1;
							}
							towerProb[tower.x][tower.y] /= changedSum;
						}
					}
				}
			}
		} else {
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]
							&& towerProb[tower.x][tower.y] != 1) {
						towerProb[tower.x][tower.y] *= (1 - binomialCoeff(numVisits[x][y], numHits[x][y])
								* Math.pow(0.75, numHits[x][y]) 
								* Math.pow(0.25, numVisits[x][y] - numHits[x][y]));
					}
				}
			}

			double totalSum = 0;
			for(int i = 0; i < towerProb.length; i++) {
				for(int j = 0; j < towerProb[i].length; j++) {
					if(currentState.inBounds(i, j) 
							&& !hasSeen[i][j]) {
						if(towerProb[i][j] < .000001) {
							towerProb[i][j] = 0;
						}
						if(towerProb[i][j] > .5) {
							towerProb[i][j] = 1;
						}
						totalSum += towerProb[i][j];
					}
				}
			}
			
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]
							&& towerProb[tower.x][tower.y] != 1) {
						towerProb[tower.x][tower.y] /= totalSum;
					}
				}
			}
		}
	}
	
	private double binomialCoeff(int numVisits, int numHits) {
		if(numHits == 0 || numVisits == numHits) {
			return 1;
		}
		double numerator = 1;
		for(int i = numVisits; i > numHits; i--) {
			numerator *= i;
		}
		double denom = 1;
		for(int i = numVisits - numHits; i > 0; i--) {
			denom *= i;
		}
		
		return numerator / denom;
	}


	public void printTowerProbs() {
		for(int i = 0; i < towerProb.length; i++) {
			for(int j = 0; j < towerProb[i].length; j++) {
				System.out.print("" + i + ", " + j + " " + Math.floor(towerProb[i][j] * 10000) / 10000 + "  ");
			}
			System.out.println();
		}
		System.out.println();
		System.out.println();
		System.out.println();
	}

	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
	}
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}
