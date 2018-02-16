package ch.ethz.matsim.baseline_scenario.transit;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.GenericEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.events.EventsReaderXMLv1.CustomEventMapper;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class PublicTransitEventMapper implements CustomEventMapper<PublicTransitEvent> {
	@Override
	public PublicTransitEvent apply(GenericEvent event) {
		double arrivalTime = event.getTime();
		Id<Person> personId = Id.create(event.getAttributes().get("person"), Person.class);
		Id<TransitLine> transitLineId = Id.create(event.getAttributes().get("line"), TransitLine.class);
		Id<TransitRoute> transitRouteId = Id.create(event.getAttributes().get("route"), TransitRoute.class);
		Id<TransitStopFacility> accessStopId = Id.create(event.getAttributes().get("access_stop"),
				TransitStopFacility.class);
		Id<TransitStopFacility> egressStopId = Id.create(event.getAttributes().get("egress_stop"),
				TransitStopFacility.class);
		double vehicleDepartureTime = Double.parseDouble(event.getAttributes().get("vehicle_departure_time"));
		double travelDistance = Double.parseDouble(event.getAttributes().get("travel_distance"));

		return new PublicTransitEvent(arrivalTime, personId, transitLineId, transitRouteId, accessStopId, egressStopId,
				vehicleDepartureTime, travelDistance);
	}
}
