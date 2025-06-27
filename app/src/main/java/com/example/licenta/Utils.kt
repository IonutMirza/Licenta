package com.example.licenta.utils

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

fun refreshAllUserStats(onDone: () -> Unit = {}) {
    val db = Firebase.firestore

    db.collection("trips").get().addOnSuccessListener { snap ->
        val sums = mutableMapOf<String, Triple<Long, Double, Double>>()

        snap.documents.forEach { doc ->
            val uid = doc.getString("uid") ?: doc.getString("userId") ?: return@forEach
            if (doc.getBoolean("finished") == false) return@forEach

            val score   = doc.getDouble("score") ?: 0.0
            val km      = (doc.getDouble("distanceMeters") ?: 0.0) / 1000.0
            val bonus   = doc.getDouble("bonusPoints")   ?: 0.0
            val points  = score * km + bonus

            val (c, s, p) = sums[uid] ?: Triple(0L, 0.0, 0.0)
            sums[uid] = Triple(c + 1, s + score, p + points)
        }

        db.collection("users").get().addOnSuccessListener { usersSnap ->
            val batch = db.batch()
            usersSnap.documents.forEach { u ->
                val (cnt, totScore, totPoints) = sums[u.id] ?: Triple(0L, 0.0, 0.0)
                batch.set(
                    db.collection("user-stats").document(u.id),
                    mapOf(
                        "tripCount"   to cnt,
                        "totalScore"  to totScore,
                        "totalPoints" to totPoints
                    )
                )
            }
            batch.commit().addOnSuccessListener { onDone() }
        }
    }
}

fun refreshUserStatsFromTrips(userId: String, onFinish: () -> Unit = {}) {
    val db = Firebase.firestore

    fun computeAndSave(docs: List<DocumentSnapshot>) {
        val valid       = docs.filter { it.getBoolean("finished") != false }
        val tripCount   = valid.size
        val totalScore  = valid.sumOf { it.getDouble("score") ?: 0.0 }
        val totalPoints = valid.sumOf {
            val score = it.getDouble("score") ?: 0.0
            val km    = (it.getDouble("distanceMeters") ?: 0.0) / 1000.0
            val bonus = it.getDouble("bonusPoints")     ?: 0.0
            score * km + bonus
        }

        db.collection("user-stats").document(userId)
            .set(
                mapOf(
                    "tripCount"   to tripCount,
                    "totalScore"  to totalScore,
                    "totalPoints" to totalPoints
                )
            )
            .addOnSuccessListener { onFinish() }
            .addOnFailureListener { onFinish() }
    }

    db.collection("trips").whereEqualTo("uid", userId).get().addOnSuccessListener { newSnap ->
        db.collection("trips").whereEqualTo("userId", userId).get()
            .addOnSuccessListener { legSnap -> computeAndSave(newSnap.documents + legSnap.documents) }
            .addOnFailureListener  {            computeAndSave(newSnap.documents) }
    }.addOnFailureListener { onFinish() }
}
