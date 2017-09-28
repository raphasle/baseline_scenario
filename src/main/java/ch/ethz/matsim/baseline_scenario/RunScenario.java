package ch.ethz.matsim.baseline_scenario;

import java.util.Random;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import ch.ethz.ivt.matsim.playgrounds.sebhoerl.utils.Downsample;
import ch.ethz.matsim.baseline_scenario.scoring.BaselineScoringFunctionFactory;
import ch.ethz.matsim.baseline_scenario.scoring.ScoreDistributionListener;
import ch.ethz.matsim.baseline_scenario.utils.ResetLegsToCarOrWalk;
import ch.ethz.matsim.mode_choice.analysis.VisitedChainCounter;
import ch.ethz.matsim.mode_choice.mnl.BasicModeChoiceParameters;
import ch.ethz.matsim.mode_choice.run.RemoveLongPlans;

public class RunScenario {
	static public void main(String[] args) {
		Config config = ConfigUtils.loadConfig(args[0]);

		config.global().setNumberOfThreads(Integer.parseInt(args[1]));
		config.qsim().setNumberOfThreads(Integer.parseInt(args[2]));
		config.controler().setWriteEventsInterval(0);
		config.controler().setWritePlansInterval(0);
		config.linkStats().setWriteLinkStatsInterval(0);
		config.counts().setWriteCountsInterval(0);
		
		UserMeeting.applyParameters(config);
		
		boolean useBestResponse = false;
		
		if (args[3].equals("old")) {
			config.controler().setOutputDirectory("simulation_old");
			UserMeeting.applyReplanningForSubtourModeChoice(config);
		} else if (args[3].equals("new")) {
			config.controler().setOutputDirectory("simulation_new");
			UserMeeting.applyReplanningForModeChoice(config);
		} else if (args[3].equals("br")) {
			config.controler().setOutputDirectory("simulation_br");
			UserMeeting.applyReplanningForModeChoice(config);
			useBestResponse = true;
		} else {
			throw new IllegalStateException();
		}
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		
		new RemoveLongPlans(10).run(scenario.getPopulation());
		new ResetLegsToCarOrWalk().run(scenario.getPopulation());
		new Downsample(0.02, new Random(0)).run(scenario.getPopulation());
		
		UserMeeting.applyModeChoice(controler, useBestResponse);

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bindScoringFunctionFactory().to(BaselineScoringFunctionFactory.class).asEagerSingleton();
				addControlerListenerBinding().to(VisitedChainCounter.class);
				addControlerListenerBinding().to(ScoreDistributionListener.class);
				addControlerListenerBinding().to(ModeShareListener.class);
			}
		});
		
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {}
		});
		
		controler.run();
	}
}
