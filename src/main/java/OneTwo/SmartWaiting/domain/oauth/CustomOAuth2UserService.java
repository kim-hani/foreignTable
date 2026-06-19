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
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MemberRepository memberRepository;

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = oAuth2User.getAttributes();
        Map<String, Object> normalizedAttributes = new HashMap<>(attributes);

        String email;
        String name;

        switch (provider) {
            case "kakao" -> {
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
                email = (String) kakaoAccount.get("email");
                name = (String) properties.get("nickname");
                // OAuth2SuccessHandler가 attributes.get("email")로 접근하므로 최상위에 주입
                normalizedAttributes.put("email", email);
            }
            case "naver" -> {
                Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                email = (String) naverResponse.get("email");
                name = (String) naverResponse.get("name");
                normalizedAttributes.put("email", email);
            }
            default -> {
                // google
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
            }
        }

        Member member = saveOrUpdate(email, name, provider);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(member.getRole().getAuthority())),
                normalizedAttributes,
                userNameAttributeName
        );
    }

    private Member saveOrUpdate(String email, String name, String provider) {
        Member member = memberRepository.findByEmail(email)
                .orElse(Member.builder()
                        .email(email)
                        .nickname(name)
                        .role(UserRole.USER)
                        .provider(provider)
                        .build());

        return memberRepository.save(member);
    }
}
