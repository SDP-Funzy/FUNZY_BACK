package com.sdp1617.backend.auth.repository;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.SocialConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialConnectionRepository extends JpaRepository<SocialConnection, Long> {

    Optional<SocialConnection> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByMember_IdAndProvider(Long memberId, AuthProvider provider);

    Optional<SocialConnection> findByMember_IdAndProvider(Long memberId, AuthProvider provider);

    long countByMember_Id(Long memberId);

    List<SocialConnection> findByMember_Id(Long memberId);
}
