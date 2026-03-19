const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * HitPay Webhook
 */
exports.hitpayWebhook = functions.https.onRequest(async (req, res) => {
    if (req.method !== 'POST') return res.status(405).send('Method Not Allowed');
    const { status, reference_number } = req.body;
    if (status === 'completed' && reference_number) {
        const db = admin.firestore();
        try {
            const snapshot = await db.collection('billings').where('meterId', '==', reference_number).where('status', '==', 'Unpaid').get();
            if (snapshot.empty) return res.status(200).send('No pending bills found.');
            const batch = db.batch();
            snapshot.docs.forEach(doc => {
                batch.update(doc.ref, { status: 'Paid', paymentMethod: 'HitPay', paidAt: admin.firestore.FieldValue.serverTimestamp() });
            });
            await batch.commit();
            return res.status(200).send('Successfully updated bills.');
        } catch (error) { return res.status(500).send('Error'); }
    }
    return res.status(400).send('Invalid');
});
