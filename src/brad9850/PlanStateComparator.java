package brad9850;

import java.util.Comparator;

public class PlanStateComparator implements Comparator<PlanState>{

	@Override
	public int compare(PlanState x, PlanState y) {
		if(x.totalDuration < y.totalDuration){
			return -1;
		}
		if(x.totalDuration > y.totalDuration){
			return 1;
		}
		return 0;
	}
	
}
