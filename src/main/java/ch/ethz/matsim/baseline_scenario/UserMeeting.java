package ch.ethz.matsim.baseline_scenario;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import ch.ethz.matsim.mode_choice.ModeChoiceModel;
import ch.ethz.matsim.mode_choice.alternatives.AsMatsimChainAlternatives;
import ch.ethz.matsim.mode_choice.alternatives.ChainAlternatives;
import ch.ethz.matsim.mode_choice.alternatives.TripChainAlternatives;
import ch.ethz.matsim.mode_choice.mnl.BasicModeChoiceAlternative;
import ch.ethz.matsim.mode_choice.mnl.BasicModeChoiceParameters;
import ch.ethz.matsim.mode_choice.mnl.ModeChoiceAlternative;
import ch.ethz.matsim.mode_choice.mnl.ModeChoiceMNL;
import ch.ethz.matsim.mode_choice.mnl.prediction.CrowflyDistancePredictor;
import ch.ethz.matsim.mode_choice.mnl.prediction.FixedSpeedPredictor;
import ch.ethz.matsim.mode_choice.mnl.prediction.HashPredictionCache;
import ch.ethz.matsim.mode_choice.mnl.prediction.NetworkPathPredictor;
import ch.ethz.matsim.mode_choice.mnl.prediction.PredictionCache;
import ch.ethz.matsim.mode_choice.mnl.prediction.PredictionCacheCleaner;
import ch.ethz.matsim.mode_choice.mnl.prediction.TripPredictor;
import ch.ethz.matsim.mode_choice.replanning.ModeChoiceStrategy;
import ch.ethz.matsim.mode_choice.utils.MatsimAlternativesReader;
import ch.ethz.matsim.mode_choice.utils.QueueBasedThreadSafeDijkstra;

public class UserMeeting {	
	static public void applyModeChoice(Controler controler, boolean useBestResponse) {
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControlerListenerBinding().to(Key.get(PredictionCacheCleaner.class, Names.named("car")));
				addPlanStrategyBinding("ModeChoiceStrategy").toProvider(ModeChoiceStrategy.class);
			}

			@Singleton
			@Provides
			@Named("car")
			public PredictionCacheCleaner providePredictionCacheCleanerForCar(@Named("car") PredictionCache cache) {
				return new PredictionCacheCleaner(cache);
			}

			@Singleton
			@Provides
			@Named("car")
			public PredictionCache providePredictionCacheForCar() {
				return new HashPredictionCache();
			}

			@Singleton
			@Provides
			public ModeChoiceModel provideModeChoiceModel(Network network, @Named("car") TravelTime travelTime,
					GlobalConfigGroup config, @Named("car") PredictionCache carCache,
					PlansCalcRouteConfigGroup routeConfig) {
				
				//ChainAlternatives chainAlternatives = new TripChainAlternatives(true);
				
				MatsimAlternativesReader reader = new MatsimAlternativesReader();
				
				Map<Id<Person>, List<List<String>>> alternatives;
				
				try {
					alternatives = reader.read(ConfigGroup.getInputFileURL(getConfig().getContext(), "chains.dat").getFile().toString());
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				
				ChainAlternatives chainAlternatives = new AsMatsimChainAlternatives(alternatives);
				
				ModeChoiceMNL model = new ModeChoiceMNL(MatsimRandom.getRandom(), chainAlternatives, network,
						useBestResponse ? ModeChoiceMNL.Mode.BEST_RESPONSE : ModeChoiceMNL.Mode.SAMPLING);

				BasicModeChoiceParameters carParameters = new BasicModeChoiceParameters(0.0, -0.62 / 1000.0,
						-23.29 / 3600.0, true);
				BasicModeChoiceParameters ptParameters = new BasicModeChoiceParameters(0.0, -0.5 / 1000.0,
						-14.43 / 3600.0, false);
				BasicModeChoiceParameters walkParameters = new BasicModeChoiceParameters(0.0, 0.0, -33.2 / 3600.0,
						false);
				BasicModeChoiceParameters bikeParameters = new BasicModeChoiceParameters(0.0, 0.0, -73.2 / 3600.0,
						true);

				TripPredictor ptPredictor = new FixedSpeedPredictor(routeConfig.getTeleportedModeSpeeds().get("pt")
						/ routeConfig.getBeelineDistanceFactors().get("pt"), new CrowflyDistancePredictor());
				TripPredictor walkPredictor = new FixedSpeedPredictor(routeConfig.getTeleportedModeSpeeds().get("walk")
						/ routeConfig.getBeelineDistanceFactors().get("walk"), new CrowflyDistancePredictor());
				TripPredictor bikePredictor = new FixedSpeedPredictor(routeConfig.getTeleportedModeSpeeds().get("bike")
						/ routeConfig.getBeelineDistanceFactors().get("bike"), new CrowflyDistancePredictor());

				TripPredictor carPredictor = new FixedSpeedPredictor(routeConfig.getTeleportedModeSpeeds().get("car")
						/ routeConfig.getBeelineDistanceFactors().get("car"), new CrowflyDistancePredictor());
				//TripPredictor carPredictor = new NetworkPathPredictor(
				//		new QueueBasedThreadSafeDijkstra(config.getNumberOfThreads(), network,
				//				new OnlyTimeDependentTravelDisutility(travelTime), travelTime));

				ModeChoiceAlternative carAlternative = new BasicModeChoiceAlternative(carParameters, carPredictor,
						carCache);
				ModeChoiceAlternative ptAlternative = new BasicModeChoiceAlternative(ptParameters, ptPredictor);
				ModeChoiceAlternative walkAlternative = new BasicModeChoiceAlternative(walkParameters, walkPredictor);
				ModeChoiceAlternative bikeAlternative = new BasicModeChoiceAlternative(bikeParameters, bikePredictor);

				model.addModeAlternative("car", carAlternative);
				model.addModeAlternative("pt", ptAlternative);
				model.addModeAlternative("walk", walkAlternative);
				model.addModeAlternative("bike", bikeAlternative);

				return model;
			}
		});
	}

	static public void applyReplanningForModeChoice(Config config) {
		config.strategy().clearStrategySettings();

		StrategySettings reroute = new StrategySettings();
		reroute.setStrategyName("ReRoute");
		reroute.setWeight(0.1);
		config.strategy().addStrategySettings(reroute);

		StrategySettings modeChoice = new StrategySettings();
		modeChoice.setStrategyName("ModeChoiceStrategy");
		modeChoice.setWeight(0.1);
		config.strategy().addStrategySettings(modeChoice);

		StrategySettings selection = new StrategySettings();
		selection.setStrategyName("ChangeExpBeta");
		selection.setWeight(0.8);
		config.strategy().addStrategySettings(selection);
	}

	static public void applyReplanningForSubtourModeChoice(Config config) {
		config.strategy().clearStrategySettings();

		StrategySettings reroute = new StrategySettings();
		reroute.setStrategyName("ReRoute");
		reroute.setWeight(0.1);
		config.strategy().addStrategySettings(reroute);

		StrategySettings subtourModeChoice = new StrategySettings();
		subtourModeChoice.setStrategyName("SubtourModeChoice");
		subtourModeChoice.setWeight(0.1);
		config.strategy().addStrategySettings(subtourModeChoice);

		StrategySettings selection = new StrategySettings();
		selection.setStrategyName("ChangeExpBeta");
		selection.setWeight(0.8);
		config.strategy().addStrategySettings(selection);

		config.subtourModeChoice().setChainBasedModes(new String[] { "car", "bike" });
		config.subtourModeChoice().setConsiderCarAvailability(true);
		config.subtourModeChoice().setModes(new String[] { "car", "bike", "pt", "walk" });
	}

	static public void applyParameters(Config config) {
		config.planCalcScore().setMarginalUtilityOfMoney(1.0);
		config.planCalcScore().setMarginalUtlOfWaiting_utils_hr(0.0);
		config.planCalcScore().setMarginalUtlOfWaitingPt_utils_hr(0.0);

		ModeParams carParameters = new ModeParams("car");
		carParameters.setConstant(0.0);
		carParameters.setMarginalUtilityOfTraveling(-23.29);
		carParameters.setMonetaryDistanceRate(-0.62 / 1000.0);

		ModeParams ptParameters = new ModeParams("pt");
		ptParameters.setConstant(0.0);
		ptParameters.setMarginalUtilityOfTraveling(-14.43);
		ptParameters.setMonetaryDistanceRate(-0.5 / 1000.0);

		ModeParams walkParameters = new ModeParams("walk");
		walkParameters.setConstant(0.0);
		walkParameters.setMarginalUtilityOfTraveling(-33.2);
		walkParameters.setMonetaryDistanceRate(0.0);

		ModeParams bikeParameters = new ModeParams("bike");
		bikeParameters.setConstant(0.0);
		bikeParameters.setMarginalUtilityOfTraveling(-73.2);
		bikeParameters.setMonetaryDistanceRate(0.0);

		config.planCalcScore().addModeParams(carParameters);
		config.planCalcScore().addModeParams(ptParameters);
		config.planCalcScore().addModeParams(walkParameters);
		config.planCalcScore().addModeParams(bikeParameters);

		ModeRoutingParams walkRoutingParams = config.plansCalcRoute().getModeRoutingParams().get("walk");
		walkRoutingParams.setBeelineDistanceFactor(1.6);
		walkRoutingParams.setTeleportedModeSpeed(5.0 * 1000.0 / 3600.0);

		ModeRoutingParams bikeRoutingParams = config.plansCalcRoute().getModeRoutingParams().get("bike");
		bikeRoutingParams.setBeelineDistanceFactor(1.6);
		bikeRoutingParams.setTeleportedModeSpeed(11.0 * 1000.0 / 3600.0);

		ModeRoutingParams ptRoutingParams = config.plansCalcRoute().getModeRoutingParams().get("pt");
		ptRoutingParams.setTeleportedModeFreespeedFactor(null);
		ptRoutingParams.setBeelineDistanceFactor(2.3);
		ptRoutingParams.setTeleportedModeSpeed(12.0 * 1000.0 / 3600.0);
		
		ModeRoutingParams carRoutingParams = new ModeRoutingParams("car");
		carRoutingParams.setTeleportedModeFreespeedFactor(null);
		carRoutingParams.setBeelineDistanceFactor(2.3);
		carRoutingParams.setTeleportedModeSpeed(20.0 * 1000.0 / 3600.0);
		config.plansCalcRoute().addModeRoutingParams(carRoutingParams);
		
		config.plansCalcRoute().setNetworkModes(Collections.emptyList());
		config.qsim().setMainModes(Collections.emptyList());
	}
}
