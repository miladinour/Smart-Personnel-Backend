package com.smartwallet.backend.service;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.PasswordResetTokenRepository;
import com.smartwallet.backend.repository.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private com.smartwallet.backend.repository.CategorieRepository categorieRepository;
    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private EmailService emailService;

    @Value("${app.server.url}")
    private String serverUrl;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec l'email: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getAuthorities())
                .disabled(!user.isEnabled())
                .build();
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec l'email: " + email));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'id: " + id));
    }

    @Transactional
    public User register(User user) throws Exception {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new Exception("Cet email est deja utilise.");
        }
        user.setMotDePasse(passwordEncoder.encode(user.getMotDePasse()));
        user.setEnabled(true); // Enabled by default for easier development/testing
        
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);

        User savedUser = userRepository.save(user);
        ensureDefaultCategories(savedUser);
        
        // Send Premium Verification Email
        emailService.sendVerificationEmail(savedUser, token, serverUrl);
        
        return savedUser;
    }
    
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Token de vérification invalide."));
        
        user.setEnabled(true);
        user.setVerificationToken(null);
        userRepository.save(user);
    }

    public User findByVerificationToken(String token) {
        return userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec ce token."));
    }

    private void ensureDefaultCategories(User user) {
        String[][] categories = {
                { "Alimentation", "DEPENSE" },
                { "Transport", "DEPENSE" },
                { "Loisirs", "DEPENSE" },
                { "Santé", "DEPENSE" },
                { "Shopping", "DEPENSE" },
                { "Logement", "DEPENSE" },
                { "Autre", "DEPENSE" },
                { "Salaire", "REVENU" },
                { "Cadeau", "REVENU" },
                { "Autre", "REVENU" }
        };

        for (String[] cat : categories) {
            if (categorieRepository.findByNomAndUser(cat[0], user).isEmpty()) {
                com.smartwallet.backend.model.Categorie c = new com.smartwallet.backend.model.Categorie();
                c.setNom(cat[0]);
                c.setType(cat[1]);
                c.setUser(user);
                c.setSystemCategory(true);
                categorieRepository.save(c);
            }
        }
    }

    public User updateUser(Long id, User userDetails) {
        User user = findById(id);

        user.setNom(userDetails.getNom());
        user.setPrenom(userDetails.getPrenom());
        user.setNumTele(userDetails.getNumTele());
        user.setPays(userDetails.getPays());
        user.setDevise(userDetails.getDevise());
        user.setGenre(userDetails.getGenre());

        // On ne change pas l'email ni le mot de passe ici par sécurité
        // (Ou alors avec des validations spécifiques)

        return userRepository.save(user);
    }

    @Transactional
    public void updateFcmToken(Long id, String token) {
        User user = findById(id);
        user.setFcmToken(token);
        userRepository.save(user);
    }

    public User updateProfilePicture(Long id, String photoUrl) {
        User user = findById(id);
        user.setPhotoProfil(photoUrl);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);

        // Supprimer les tokens de réinitialisation liés à l'utilisateur
        passwordResetTokenRepository.deleteByUser(user);

        // Supprimer l'utilisateur (et la ligne parente dans personne grâce à
        // @Transactional)
        userRepository.delete(user);
    }

    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(oldPassword, user.getMotDePasse())) {
            throw new RuntimeException("Ancien mot de passe incorrect");
        }
        user.setMotDePasse(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
    @Transactional
    public void updateUserStatus(Long id, boolean enabled) {
        User user = findById(id);
        user.setEnabled(enabled);
        userRepository.save(user);
    }

    @Transactional
    public void updateSolde(Long userId, java.math.BigDecimal amountChange) {
        User user = findById(userId);
        user.setSoldeTotal(user.getSoldeTotal().add(amountChange));
        userRepository.save(user);
    }
}
