package ch.ethz.matsim.baseline_scenario.analysis.trips.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.vehicles.Vehicle;

import ch.ethz.matsim.baseline_scenario.analysis.trips.TripItem;
import ch.ethz.matsim.baseline_scenario.analysis.trips.utils.HomeActivityTypes;

public class TripListener implements ActivityStartEventHandler, ActivityEndEventHandler, PersonDepartureEventHandler,
		PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, LinkEnterEventHandler, TeleportationArrivalEventHandler {
	final private StageActivityTypes stageActivityTypes;
	final private HomeActivityTypes homeActivityTypes;
	final private MainModeIdentifier mainModeIdentifier;
	final private Network network;
	final private PopulationFactory factory;

	final private Collection<TripItem> trips = new LinkedList<>();
	final private Map<Id<Person>, TripListenerItem> ongoing = new HashMap<>();
	final private Map<Id<Vehicle>, Set<Id<Person>>> passengers = new HashMap<>();
	final private Map<Id<Person>, Integer> tripIndex = new HashMap<>();

	public TripListener(Network network, StageActivityTypes stageActivityTypes, HomeActivityTypes homeActivityTypes,
			MainModeIdentifier mainModeIdentifier) {
		this.network = network;
		this.stageActivityTypes = stageActivityTypes;
		this.homeActivityTypes = homeActivityTypes;
		this.mainModeIdentifier = mainModeIdentifier;
		this.factory = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
	}

	public Collection<TripItem> getTripItems() {
		return trips;
	}

	@Override
	public void reset(int iteration) {
		trips.clear();
		ongoing.clear();
		passengers.clear();
		tripIndex.clear();
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (event.getPersonId().toString().contains("av")) return;
		if (event.getPersonId().toString().startsWith("pt")) return;
		
		if (!stageActivityTypes.isStageActivity(event.getActType())) {
			Integer personTripIndex = tripIndex.get(event.getPersonId());
			network.getLinks().get(event.getLinkId()).getCoord();

			if (personTripIndex == null) {
				personTripIndex = 0;
			} else {
				personTripIndex = personTripIndex + 1;
			}

			ongoing.put(event.getPersonId(), new TripListenerItem(event.getPersonId(), personTripIndex,
					network.getLinks().get(event.getLinkId()).getCoord(), event.getTime(), event.getActType()));

			tripIndex.put(event.getPersonId(), personTripIndex);
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (event.getPersonId().toString().contains("av")) return;
		if (event.getPersonId().toString().startsWith("pt")) return;
		
		ongoing.get(event.getPersonId()).elements.add(factory.createLeg(event.getLegMode()));
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (event.getPersonId().toString().contains("av")) return;
		if (event.getPersonId().toString().startsWith("pt")) return;
		
		if (stageActivityTypes.isStageActivity(event.getActType())) {
			ongoing.get(event.getPersonId()).elements
					.add(factory.createActivityFromLinkId(event.getActType(), event.getLinkId()));
		} else {
			TripListenerItem trip = ongoing.remove(event.getPersonId());

			if (trip != null) {
				trip.returning = homeActivityTypes.isHomeActivity(event.getActType());
				trip.purpose = trip.returning ? trip.startPurpose : event.getActType();
				trip.travelTime = event.getTime() - trip.startTime;
				trip.mode = mainModeIdentifier.identifyMainMode(trip.elements);
				trip.destination = network.getLinks().get(event.getLinkId()).getCoord();
				trip.networkDistance = getNetworkDistance(trip) / 1000;
				trip.crowflyDistance = CoordUtils.calcEuclideanDistance(trip.origin, trip.destination) / 1000.0;

				trips.add(new TripItem(trip.personId, trip.personTripId, trip.origin, trip.destination, trip.startTime,
						trip.travelTime, trip.networkDistance, trip.mode, trip.purpose, trip.returning, trip.crowflyDistance));
			}
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!passengers.containsKey(event.getVehicleId())) {
			passengers.put(event.getVehicleId(), new HashSet<>());
		}
		
		passengers.get(event.getVehicleId()).add(event.getPersonId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		passengers.get(event.getVehicleId()).remove(event.getPersonId());
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (passengers.containsKey(event.getVehicleId())) {
			for (Id<Person> passengerId: passengers.get(event.getVehicleId())) {
				if (passengerId.toString().contains("av")) continue;
				if (passengerId.toString().startsWith("pt")) continue;
				
				ongoing.get(passengerId).route.add(event.getLinkId());
			}
		}
	}

	private double getNetworkDistance(TripListenerItem trip) {
		String mainMode = mainModeIdentifier.identifyMainMode(trip.elements);
		
		if (mainMode.equals("car") || mainMode.equals("av")) {
			double distance = 0.0;
			
			if (trip.route.size() > 0) {
				for (Id<Link> linkId : trip.route.subList(0, trip.route.size() - 1)) {
					distance += network.getLinks().get(linkId).getLength();
				}
			}
			
			return distance;
		}
		
		return trip.networkDistance;
	}

	@Override
	public void handleEvent(TeleportationArrivalEvent event) {
		TripListenerItem item = ongoing.get(event.getPersonId());
		
		if (Double.isNaN(item.networkDistance)) {
			item.networkDistance = 0.0;
		}
		
		item.networkDistance += event.getDistance();
	}
}
