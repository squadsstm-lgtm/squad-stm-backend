package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.WalkthroughPages;
import com.squad.backend.model.Auth;
import com.squad.backend.repository.AuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WalkthroughService {

    private final MongoTemplate mongoTemplate;
    private final AuthRepository authRepository;

    /**
     * Adds one page key if it is not already there. One indexed read of this account, one add-to-set write.
     */
    public List<String> markSeen(String authId, String page) {
        String key = page == null ? "" : page.trim().toLowerCase();
        if (!WalkthroughPages.isKnown(key)) {
            throw new IllegalArgumentException("Unknown walkthrough page");
        }
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        if (Boolean.TRUE.equals(auth.getIsBlocked()) || Boolean.TRUE.equals(auth.getIsInactive())) {
            throw new IllegalArgumentException(
                    Boolean.TRUE.equals(auth.getIsBlocked()) ? ErrorMessages.USER_BLOCKED : ErrorMessages.USER_INACTIVE);
        }

        Query query = Query.query(Criteria.where("_id").is(authId));
        Update update = new Update().addToSet("seenWalkthroughPages", key);
        mongoTemplate.updateFirst(query, update, Auth.class);

        Set<String> seen = auth.getSeenWalkthroughPages() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(auth.getSeenWalkthroughPages());
        seen.add(key);
        return new ArrayList<>(seen);
    }
}
