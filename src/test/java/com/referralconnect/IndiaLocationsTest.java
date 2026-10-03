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
        equal("Bengaluru", IndiaLocations.cities("Bangaluru Karnataka India"));
        equal(IndiaLocations.OTHER_INDIA, IndiaLocations.cities("Karnataka, India"), "a state alone is not a city");
        equal("Bengaluru, Hyderabad", IndiaLocations.cities("Bangalore, IND; Hyderabad, IND"));
        equal("Delhi NCR", IndiaLocations.cities("Noida, Uttar Pradesh"));
        equal("Mumbai", IndiaLocations.cities("Mumbai, India"));
        equal(IndiaLocations.REMOTE_INDIA, IndiaLocations.cities("Remote, India"));
        equal("Pune, " + IndiaLocations.REMOTE_INDIA, IndiaLocations.cities("Pune / Remote India"));
        equal(IndiaLocations.OTHER_INDIA, IndiaLocations.cities("Kochi, Kerala"));
    }

    @Test
    void displayKeepsEachPlaceOnceWithoutCodes() {
        // Real location strings from the live scan.
        equal("Bengaluru, Karnataka", show("Bengaluru, Karnataka, IND / IN, KA, Bengaluru"));
        equal("Chennai, Tamil Nadu", show("Chennai, Tamil Nadu, IND / IN, TN, Chennai"));
        equal("Hyderabad", show("hyderabad, , India / hyderabad"));
        equal("Hosur Road Bangalore", show("hosur road bangalore, , India / hosur road bangalore"));
        equal("Bengaluru Karnataka", show("India Bengaluru Karnataka / Bangalore"));
        equal("Bangalore", show("Bangalore, India / Bangalore, India (ZIN110)"));
        equal("Bengaluru, Karnataka / Mumbai, Maharashtra",
                show("Bengaluru, Karnataka, India / Mumbai, Maharashtra, India"));
        equal("Coimbatore / Kochi", show("Coimbatore, TN, India / Coimbatore / Kochi"));
        equal("Remote", show("Remote - India"));
        equal("Remote", show("India (Remote)"));
        equal("Offsite", show("India Offsite (ZIN99), More..."));
        equal("India", show("India"));
        equal("Bengaluru", show("IN - Bengaluru, India"));
        equal("Hyderabad 115 IT Park Area", show("IND-Hyderabad 115 IT Park Area"));
        equal("Bengaluru-EPIP 122 (Phase II)", show("IND19-01-Bengaluru-EPIP 122 (Phase II)"));
        equal("Indore", show("Indore, India"));
    }

    private static String show(String location) {
        return IndiaLocations.display(java.util.List.of(location));
    }

    @Test
    void filterChoicesIncludeRemoteAndOther() {
        check(IndiaLocations.cityChoices().contains("Bengaluru"), "Bengaluru offered");
        check(IndiaLocations.cityChoices().contains(IndiaLocations.REMOTE_INDIA), "remote offered");
        check(IndiaLocations.cityChoices().contains(IndiaLocations.OTHER_INDIA), "other offered");
    }
}
