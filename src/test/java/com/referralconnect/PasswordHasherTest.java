package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.service.PasswordHasher;

import static com.referralconnect.TestRunner.check;

class PasswordHasherTest {

    @Test
    void verifiesOnlyTheRightPassword() {
        PasswordHasher.Hashed h = PasswordHasher.hash("correct horse");
        check(PasswordHasher.verify("correct horse", h.hash(), h.salt()), "right password");
        check(!PasswordHasher.verify("correct horsE", h.hash(), h.salt()), "wrong password");
        check(!PasswordHasher.verify("correct horse", "", ""), "missing hash");
    }

    @Test
    void saltsEveryHash() {
        PasswordHasher.Hashed a = PasswordHasher.hash("same password");
        PasswordHasher.Hashed b = PasswordHasher.hash("same password");
        check(!a.salt().equals(b.salt()), "different salts");
        check(!a.hash().equals(b.hash()), "different hashes");
    }
}
