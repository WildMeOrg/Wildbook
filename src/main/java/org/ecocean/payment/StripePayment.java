import java.util.HashMap;
import java.util.Map;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;

public class StripePayment {

    public static void main(String[] args) {
        RequestOptions requestOptions = (new RequestOptionsBuilder()).setApiKey("YOUR-SECRET-KEY").build();
        Map<String, Object> chargeMap = new HashMap<String, Object>();
        chargeMap.put("amount", 100);
        chargeMap.put("currency", "usd");
        Map<String, Object> cardMap = new HashMap<String, Object>();
        cardMap.put("number", "4242424242424242");
        cardMap.put("exp_month", 12);
        cardMap.put("exp_year", 2020);
        chargeMap.put("card", cardMap);
        try {
            Charge charge = Charge.create(chargeMap, requestOptions);
            System.out.println(charge);
        } catch (StripeException e) {
            e.printStackTrace();
        }
    }
}
