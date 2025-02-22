package vn.com.fecredit.app.service;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.com.fecredit.app.dto.GoldenHourDTO;
import vn.com.fecredit.app.dto.RewardDTO;
import vn.com.fecredit.app.exception.ResourceNotFoundException;
import vn.com.fecredit.app.mapper.GoldenHourMapper;
import vn.com.fecredit.app.mapper.RewardMapper;
import vn.com.fecredit.app.model.GoldenHour;
import vn.com.fecredit.app.model.Reward;
import vn.com.fecredit.app.repository.GoldenHourRepository;
import vn.com.fecredit.app.repository.RewardRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RewardService {

    private static final Logger logger = LoggerFactory.getLogger(RewardService.class);

    private final RewardRepository rewardRepository;
    private final RewardMapper rewardMapper;
    private final GoldenHourRepository goldenHourRepository;
    private final GoldenHourMapper goldenHourMapper;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<RewardDTO> getAllRewards() {
        return rewardRepository.findAll().stream()
                .map(rewardMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RewardDTO getRewardById(Long id) {
        return rewardRepository.findById(id)
                .map(rewardMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found with id: " + id));
    }

    public RewardDTO createReward(RewardDTO.CreateRewardRequest request) {
        Reward reward = rewardMapper.toEntity(request);
        reward = rewardRepository.save(reward);
        return rewardMapper.toDTO(reward);
    }

    public RewardDTO updateReward(Long id, RewardDTO.UpdateRewardRequest request) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found with id: " + id));

        rewardMapper.updateRewardFromRequest(request, reward);
        reward = rewardRepository.save(reward);
        return rewardMapper.toDTO(reward);
    }

    public RewardDTO updateQuantity(Long id, Integer quantity) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found with id: " + id));

        reward.setQuantity(quantity);
        if (quantity < reward.getRemainingQuantity()) {
            reward.setRemainingQuantity(quantity);
        }
        reward = rewardRepository.save(reward);
        return rewardMapper.toDTO(reward);
    }

    public void deleteReward(Long id) {
        if (!rewardRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reward not found with id: " + id);
        }
        rewardRepository.deleteById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean decrementRemainingQuantity(Long id) {
        try {
            // Fetch the reward entity
            Reward reward = rewardRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Reward not found"));

            // Apply pessimistic write lock
//            entityManager.lock(reward, LockModeType.PESSIMISTIC_WRITE);

            // Check if remaining quantity is available
            if (reward.getRemainingQuantity() > 0) {
//                reward.setRemainingQuantity(reward.getRemainingQuantity() - 1);
//                rewardRepository.save(reward);
                int updateCount = rewardRepository.decrementRemainingQuantity(id);
                return updateCount > 0;
            } else {
                return false; // Quantity is already zero, cannot decrement
            }
        } catch (Exception e) {
            log.error("Error decrementing remaining quantity for reward {}: {}", id, e.getMessage());
            throw e; // or handle accordingly
        }
    }

    @Transactional
    public RewardDTO addGoldenHour(Long rewardId, GoldenHourDTO.CreateRequest request) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found with id: " + rewardId));

        GoldenHour goldenHour = goldenHourMapper.toEntity(request);
        goldenHour.setReward(reward);
        goldenHourRepository.save(goldenHour);

        reward.getGoldenHours().add(goldenHour);
        Reward savedReward = rewardRepository.save(reward);

        return rewardMapper.toDTO(savedReward);
    }

    @Transactional
    public RewardDTO removeGoldenHour(Long rewardId, Long goldenHourId) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found with id: " + rewardId));

        GoldenHour goldenHour = goldenHourRepository.findById(goldenHourId)
                .orElseThrow(() -> new ResourceNotFoundException("Golden hour not found with id: " + goldenHourId));

        if (!goldenHour.getReward().getId().equals(rewardId)) {
            throw new IllegalArgumentException("Golden hour does not belong to this reward");
        }

        reward.getGoldenHours().remove(goldenHour);
        goldenHourRepository.delete(goldenHour);
        Reward savedReward = rewardRepository.save(reward);

        return rewardMapper.toDTO(savedReward);
    }
}