package travelsafe.paypal;

import com.paypal.api.payments.Links;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import travelsafe.model.TravelInsurance;
import travelsafe.repository.TravelInsuranceRepository;
import travelsafe.service.impl.PriceCalculatorService;
import travelsafe.service.impl.TravelInsuranceService;

import java.util.HashMap;

/**
 * Created by aloha on 27-Nov-16.
 */
@RestController
@RequestMapping("/api")
public class PayPalController {

    private static final Logger LOG = LoggerFactory.getLogger(PayPalController.class);

    @Autowired
    TravelInsuranceService travelInsuranceService;

    @Autowired
    TravelInsuranceRepository travelInsuranceRepository;

    @Autowired
    PayPalService payPalService;

    @Autowired
    PriceCalculatorService priceCalculatorService;

    /**
     * When user clicks on BUY ITEM button
     * @see PayPalService
     * */
    @RequestMapping(value = "/paypal/create",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity createPayment(@RequestBody TravelInsurance travelInsurance) {

        LOG.debug("Request for creating payment for travel insurance with {} ID",travelInsurance.getId());

        if (travelInsurance.getId() != null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            /* [1] calculate final price and save entity do DB*/
            priceCalculatorService.calculatePrice(travelInsurance);

            TravelInsurance savedTravelInsurance = travelInsuranceService.save(travelInsurance);
            System.out.println("SAVED with ID:");
            System.out.println(savedTravelInsurance.getId());
            LOG.debug("Saved with {} ID", savedTravelInsurance.getId());

            /* [2] get paypal link - create payment*/
            Links links = payPalService.createPayment(savedTravelInsurance.getId(), travelInsurance.getTotalPrice(), 0, travelInsurance.getTotalPrice(), "Travel Insurance Package by Travel Safe, Inc.");

            /* [3] pack data to response*/
            HashMap<String, Object> response = new HashMap<>();
            response.put("link", links);
            response.put("item", savedTravelInsurance);

            /* [4]return packed data*/
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * When users is redirected to our website to confirm payment (execute it) or cancel it (delte order from DB)
     * Same url in both cases - angular opusteno :D
     * @see PayPalService
     * */
    @RequestMapping(value = "/paypal/execute/{orderId}/{paymentId}/{payerId}",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity executePayment(@PathVariable Long orderId, @PathVariable String paymentId, @PathVariable String payerId) {

        LOG.debug("Execute payment with {} order ID, {} payment ID and {} payer ID.");

        TravelInsurance order = travelInsuranceRepository.getOne(orderId);

        //it that order exists and has not payed yet -> execute payment
        if(order!=null && order.getPaypalPaymentId()!=null){

            boolean status = payPalService.executePayment(payerId, paymentId);
            LOG.debug("Status of payment {}",status);
            if(status){
                //update order
                order.setPaypalPaymentId(paymentId);
                travelInsuranceRepository.save(order);
                return new ResponseEntity<>(HttpStatus.OK);
            }
            else {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }



}
