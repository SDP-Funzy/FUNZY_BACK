package com.sdp1617.backend.letter.repository;

import com.sdp1617.backend.letter.entity.ReceivedLetter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReceivedLetterRepository extends JpaRepository<ReceivedLetter, Long>, JpaSpecificationExecutor<ReceivedLetter> {

    boolean existsByIdAndReceiverMemberId(Long id, Long receiverMemberId);

    List<ReceivedLetter> findByReceiverMemberId(Long receiverMemberId);
}
