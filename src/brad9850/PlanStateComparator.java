package brad9850;

import java.util.Comparator;

public class PlanStateComparator implements Comparator<PlanState>{

	@Override
	public int compare(PlanState x, PlanState y) {
		//First, check duration
		if(x.totalDuration < y.totalDuration){
			return -1;
		}
		if(x.totalDuration > y.totalDuration){
			return 1;
		}
		//If duration is equal, check number of ships
		if(x.shipCount > y.shipCount){
			return -1;
		}
		if(x.shipCount < y.shipCount){
			return 1;
		}
		//If the number of ships is equal, check number of bases
		if(x.baseCount > y.baseCount){
			return -1;
		}
		if(x.baseCount < y.baseCount){
			return 1;
		}
		//If the number of bases is equal, check the total resources
		if(x.totalResources.getTotal() > y.totalResources.getTotal()){
			return -1;
		}
		if(x.totalResources.getTotal() < y.totalResources.getTotal()){
			return 1;
		}
		return 0;
	}
	
}
