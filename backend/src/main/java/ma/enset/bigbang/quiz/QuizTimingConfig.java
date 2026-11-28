package ma.enset.bigbang.quiz;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class QuizTimingConfig {
 @Bean Clock quizClock(){return Clock.systemUTC();}
}
