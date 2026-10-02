package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.scan.IndiaLocations;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;

class IndiaLocationsTest {

    @Test
    void recognisesIndianLocations() {
        check(IndiaLocations.isIndia("Bengaluru, Karnataka, India"), "Bengaluru");
        check(IndiaLocations.isIndia("Bangalore, IND; Hyderabad, IND"), "Bangalore");
        check(IndiaLocations.isIndia("Gurugram"), "Gurugram");
        check(IndiaLocations.isIndia("Pune, IN"), "Pune");
        check(IndiaLocations.isIndia("Remote - India"), "remote India");
        check(IndiaLocations.isIndia("Jaipur"), "Jaipur");
    }

    @Test
    void rejectsLookalikes() {
        check(!IndiaLocations.isIndia("Indianapolis, IN"), "Indianapolis");
        check(!IndiaLocations.isIndia("Indiana"), "Indiana");
        check(!IndiaLocations.isIndia("San Francisco, CA"), "SF");
        check(!IndiaLocations.isIndia("Remote"), "plain remote");
        check(!IndiaLocations.isIndia(""), "empty");
        check(!IndiaLocations.isIndia(null), "null");
    }

    @Test
    void namesCanonicalCities() {
        equal("Bengaluru", IndiaLocations.cities("Bangalore"));
        equal("Bengaluru, Hyderabad", IndiaLocations.cities("Bangalore, IND; Hyderabad, IND"));
        equal("Delhi NCR", IndiaLocations.cities("Noida, Uttar Pradesh"));
        equal("Mumbai", IndiaLocations.cities("Mumbai, India"));
        equal(IndiaLocations.REMOTE_INDIA, IndiaLocations.cities("Remote, India"));
        equal("Pune, " + IndiaLocations.REMOTE_INDIA, IndiaLocations.cities("Pune / Remote India"));
        equal(IndiaLocations.OTHER_INDIA, IndiaLocations.cities("Kochi, Kerala"));
    }

    @Test
    void filterChoicesIncludeRemoteAndOther() {
        check(IndiaLocations.cityChoices().contains("Bengaluru"), "Bengaluru offered");
        check(IndiaLocations.cityChoices().contains(IndiaLocations.REMOTE_INDIA), "remote offered");
        check(IndiaLocations.cityChoices().contains(IndiaLocations.OTHER_INDIA), "other offered");
    }
}
