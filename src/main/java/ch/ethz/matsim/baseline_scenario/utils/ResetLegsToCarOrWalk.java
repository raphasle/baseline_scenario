package ch.ethz.matsim.baseline_scenario.utils;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.router.TripStructureUtils;

public class ResetLegsToCarOrWalk {
	public void run(Population population) {
		for (Person person : population.getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				if (PersonUtils.hasLicense(person) && !(PersonUtils.getCarAvail(person).equals("never") || PersonUtils.getLicense(person).equals("no"))) {
					TripStructureUtils.getLegs(plan).forEach(leg -> { leg.setMode("car"); leg.setRoute(null); });
				} else {
					TripStructureUtils.getLegs(plan).forEach(leg -> { leg.setMode("walk"); leg.setRoute(null); });
				}
			}
		}
	}
}
