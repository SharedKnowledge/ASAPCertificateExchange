package net.sharksystem.asap.persons;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPSecurityException;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.asap.crypto.InMemoASAPKeyStore;
import net.sharksystem.asap.internals.ASAPEngine;
import net.sharksystem.asap.internals.ASAPEngineFS;
import net.sharksystem.asap.crypto.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.security.*;
import java.util.Collection;

public class ASAPCertificateStoreTests {
    private static final String ROOT_DIRECTORY = "asapStorageRootDirectory/";
    private static final String ROOT_DIRECTORY_ALICE = "asapStorageRootDirectory/alice/";
    private static final String ROOT_DIRECTORY_BOB = "asapStorageRootDirectory/bob/";
    private static final String ROOT_DIRECTORY_CLARA = "asapStorageRootDirectory/clara/";
    private static final String ROOT_DIRECTORY_DAVID = "asapStorageRootDirectory/david/";
    private static final CharSequence ALICE_ID = "42";
    private static final CharSequence ALICE_NAME = "Alice";
    private static final CharSequence BOB_ID = "43";
    private static final CharSequence BOB_NAME = "Bob";
    private static final CharSequence CLARA_ID = "44";
    private static final CharSequence CLARA_NAME = "Clara";
    private static final CharSequence DAVID_ID = "45";
    private static final CharSequence DAVID_NAME = "David";

    private void assertCertificateEquals(ASAPCertificate a, ASAPCertificate b) {
        Assert.assertEquals(a.getIssuerID(), b.getIssuerID());
        Assert.assertEquals(a.getSubjectID(), b.getSubjectID());
        Assert.assertEquals(a.getSubjectName(), b.getSubjectName());
        Assert.assertEquals(a.getIssuerName(), b.getIssuerName());
    }

    @Test
    public void identityAssuranceCalculationTest() throws IOException, ASAPException {
        ASAPEngineFS.removeFolder(ROOT_DIRECTORY);

        long now = System.currentTimeMillis();

        // setup alice
        ASAPEngine aliceASAPStorage = ASAPEngineFS.getASAPStorage(
                "Alice", ROOT_DIRECTORY_ALICE, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapAliceCertificateStorage =
                new ASAPAbstractCertificateStore(aliceASAPStorage, ALICE_ID, ALICE_NAME);

        ASAPKeyStore aliceCryptoStorage = new InMemoASAPKeyStore(ALICE_ID);
        ASAPCertificateStore aliceASAPCertificateStore = new ASAPCertificateStoreImpl(asapAliceCertificateStorage, aliceCryptoStorage);

        // setup bob
        ASAPEngine bobASAPStorage = ASAPEngineFS.getASAPStorage(
                "Alice", ROOT_DIRECTORY_BOB, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapBobCertificateStorage =
                new ASAPAbstractCertificateStore(aliceASAPStorage, BOB_ID, BOB_NAME);

        ASAPKeyStore bobCryptoStorage = new InMemoASAPKeyStore(BOB_ID);
        ASAPCertificateStore bobASAPCertificateStore = new ASAPCertificateStoreImpl(asapBobCertificateStorage, bobCryptoStorage);

        // simulation - Bob must send its credentials in some way to Alice - assume that happened
        CharSequence bobID = bobASAPCertificateStore.getOwnerID();
        CharSequence bobName = bobASAPCertificateStore.getOwnerName();
        PublicKey bobPublicKey = bobASAPCertificateStore.getPublicKey();

        // alice signs a certificate of bob
        ASAPCertificate asapCertificate = aliceASAPCertificateStore.addAndSignPerson(bobID, bobName, bobPublicKey, now);

        // Alice could (and should) send it back to Bob - not tested here
        byte[] bytes = asapCertificate.asBytes();

        Assert.assertEquals(OtherPerson.HIGHEST_IDENTITY_ASSURANCE_LEVEL,
                aliceASAPCertificateStore.getIdentityAssurance(bobID));

        // create a certificate of David issued by Clara
        // setup Clara
        ASAPEngine claraASAPStorage = ASAPEngineFS.getASAPStorage(
                "Clara", ROOT_DIRECTORY_CLARA, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapClaraCertificateStorage =
                new ASAPAbstractCertificateStore(claraASAPStorage, CLARA_ID, CLARA_NAME);

        ASAPKeyStore claraCryptoStorage = new InMemoASAPKeyStore(CLARA_ID);
        ASAPCertificateStore claraASAPCertificateStore = new ASAPCertificateStoreImpl(asapClaraCertificateStorage, claraCryptoStorage);

        // setup David
        ASAPEngine davidASAPStorage = ASAPEngineFS.getASAPStorage(
                "Clara", ROOT_DIRECTORY_DAVID, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapDavidCertificateStorage =
                new ASAPAbstractCertificateStore(davidASAPStorage, DAVID_ID, DAVID_NAME);

        ASAPKeyStore davidCryptoStorage = new InMemoASAPKeyStore(DAVID_ID);
        ASAPCertificateStore davidASAPCertificateStore = new ASAPCertificateStoreImpl(asapDavidCertificateStorage, davidCryptoStorage);

        // clara signs a certificate of david
        CharSequence davidID = davidASAPCertificateStore.getOwnerID();
        asapCertificate = claraASAPCertificateStore.addAndSignPerson(
                davidID,
                davidASAPCertificateStore.getOwnerName(),
                davidASAPCertificateStore.getPublicKey(), now);

        // add to alice certification storage
        aliceASAPCertificateStore.addCertificate(asapCertificate);

        Collection<ASAPCertificate> davidCerts = aliceASAPCertificateStore.getCertificatesBySubject(davidID);
        Assert.assertNotNull(davidCerts);
        Assert.assertEquals(1, davidCerts.size());

        // alice cannot verify clara - there is no safe way to david
        Assert.assertEquals(OtherPerson.LOWEST_IDENTITY_ASSURANCE_LEVEL,
                aliceASAPCertificateStore.getIdentityAssurance(davidID));

        // bob signs a certificate of clara
        CharSequence claraID = claraASAPCertificateStore.getOwnerID();
        asapCertificate = bobASAPCertificateStore.addAndSignPerson(
                claraID,
                claraASAPCertificateStore.getOwnerName(),
                claraASAPCertificateStore.getPublicKey(), now);

        // add to alice certification storage
        aliceASAPCertificateStore.addCertificate(asapCertificate);

        // alice can verify clara thanks to bob
        int claraIdentityAssurance = aliceASAPCertificateStore.getIdentityAssurance(claraID);
        System.out.println("clara identity assurance on alice side == " + claraIdentityAssurance);
        Assert.assertEquals(5, claraIdentityAssurance);

        // alice can verify david thanks to bob and clara
        int davidIdentityAssurance = aliceASAPCertificateStore.getIdentityAssurance(davidID);
        System.out.println("david identity assurance on alice side == " + davidIdentityAssurance);
        // there is a way from alice to david now - iA is 2.5 rounded up to 3
        Assert.assertEquals(3, aliceASAPCertificateStore.getIdentityAssurance(davidID));
    }

    @Test
    public void certificateVerifyTest1() throws
            IOException, ASAPException, NoSuchAlgorithmException, SignatureException,
            InvalidKeyException, ASAPSecurityException {

        ASAPEngineFS.removeFolder(ROOT_DIRECTORY);

        long now = System.currentTimeMillis();

        // setup alice
        ASAPEngine aliceASAPStorage = ASAPEngineFS.getASAPStorage(
                "Alice", ROOT_DIRECTORY_ALICE, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapAliceCertificateStorage =
                new ASAPAbstractCertificateStore(aliceASAPStorage, ALICE_ID, ALICE_NAME);
        ASAPKeyStore aliceCryptoStorage = new InMemoASAPKeyStore(ALICE_ID);
        ASAPCertificateStore aliceASAPCertificateStore = new ASAPCertificateStoreImpl(asapAliceCertificateStorage, aliceCryptoStorage);

        // setup bob
        ASAPEngine bobASAPStorage = ASAPEngineFS.getASAPStorage(
                "Alice", ROOT_DIRECTORY_BOB, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapBobCertificateStorage =
                new ASAPAbstractCertificateStore(aliceASAPStorage, BOB_ID, BOB_NAME);
        ASAPKeyStore bobCryptoStorage = new InMemoASAPKeyStore(BOB_ID);
        ASAPCertificateStore bobASAPCertificateStore = new ASAPCertificateStoreImpl(asapBobCertificateStorage, bobCryptoStorage);

        // simulation - Bob must send its credentials in some way to Alice - assume that happened
        CharSequence bobID = bobASAPCertificateStore.getOwnerID();
        CharSequence bobName = bobASAPCertificateStore.getOwnerName();
        PublicKey bobPublicKey = bobASAPCertificateStore.getPublicKey();

        PublicKey alicePublicKey = aliceASAPCertificateStore.getPublicKey();
        PrivateKey alicePrivateKey = aliceASAPCertificateStore.getPrivateKey();

        // alice signs a certificate of bob
        ASAPCertificate asapCertificate = ASAPCertificateImpl.produceCertificate(
                aliceASAPCertificateStore.getOwnerID(), aliceASAPCertificateStore.getOwnerName(), alicePrivateKey,
                bobID, bobName, bobPublicKey, now, ASAPCertificateImpl.DEFAULT_SIGNATURE_METHOD);

        // verify
        boolean verified = asapCertificate.verify(alicePublicKey);
        Assert.assertTrue(verified);
    }

    @Test
    public void certificateVerifyTest2() throws
            IOException, ASAPException, NoSuchAlgorithmException, SignatureException,
            InvalidKeyException, ASAPSecurityException {

        ASAPEngineFS.removeFolder(ROOT_DIRECTORY);

        long now = System.currentTimeMillis();

        // setup alice
        ASAPEngine aliceASAPStorage = ASAPEngineFS.getASAPStorage(
                "Alice", ROOT_DIRECTORY_ALICE, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapAliceCertificateStorage =
                new ASAPAbstractCertificateStore(aliceASAPStorage, ALICE_ID, ALICE_NAME);
        ASAPKeyStore aliceCryptoStorage = new InMemoASAPKeyStore(ALICE_ID);
        ASAPCertificateStore aliceASAPCertificateStore = new ASAPCertificateStoreImpl(asapAliceCertificateStorage, aliceCryptoStorage);

        // setup bob
        ASAPEngine bobASAPStorage = ASAPEngineFS.getASAPStorage(
                "Alice", ROOT_DIRECTORY_BOB, ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        ASAPCertificateStorage asapBobCertificateStorage =
                new ASAPAbstractCertificateStore(aliceASAPStorage, BOB_ID, BOB_NAME);
        ASAPKeyStore bobCryptoStorage = new InMemoASAPKeyStore(BOB_ID);
        ASAPCertificateStore bobASAPCertificateStore = new ASAPCertificateStoreImpl(asapBobCertificateStorage, bobCryptoStorage);

        // simulation - Bob must send its credentials in some way to Alice - assume that happened
        CharSequence bobID = bobASAPCertificateStore.getOwnerID();
        CharSequence bobName = bobASAPCertificateStore.getOwnerName();
        PublicKey bobPublicKey = bobASAPCertificateStore.getPublicKey();

        PublicKey alicePublicKey = aliceASAPCertificateStore.getPublicKey();
        PrivateKey alicePrivateKey = aliceASAPCertificateStore.getPrivateKey();

        // alice signs a certificate of bob
        ASAPCertificate asapCertificate = aliceASAPCertificateStore.addAndSignPerson(bobID, bobName, bobPublicKey, now);

        // verify
        Assert.assertTrue(asapCertificate.verify(alicePublicKey));
    }
}