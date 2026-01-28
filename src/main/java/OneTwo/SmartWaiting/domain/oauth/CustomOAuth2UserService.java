package OneTwo.SmartWaiting.domain.oauth;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 구글 서버에서 유저 정보 가져오기
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // 2. 데이터 추출
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName(); // "sub"

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        // 3. 저장 또는 업데이트
        Member member = saveOrUpdate(email, name);

        // 4. 시큐리티용 유저 객체 반환
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(member.getRole().getAuthority())),
                attributes,
                userNameAttributeName
        );
    }

    private Member saveOrUpdate(String email, String name) {
        Member member = memberRepository.findByEmail(email)
                .orElse(Member.builder()
                        .email(email)
                        .nickname(name)
                        .role(UserRole.USER) // 기본 권한
                        .build());

        return memberRepository.save(member);
    }
}
