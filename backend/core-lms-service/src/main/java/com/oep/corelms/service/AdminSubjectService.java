package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CreateSubjectRequest;
import com.oep.corelms.dto.admin.SubjectResponse;
import com.oep.corelms.dto.admin.UpdateSubjectRequest;
import com.oep.corelms.repository.SubjectRepository;
import com.oep.coremodel.entity.course.Subject;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Admin manages the list of subjects (môn học) the center teaches. */
@Service
@RequiredArgsConstructor
public class AdminSubjectService {

    private final SubjectRepository subjects;

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        if (subjects.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.NAME_ALREADY_EXISTS, "Subject name already exists");
        }
        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setDescription(request.description());
        return SubjectResponse.from(subjects.save(subject));
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> list() {
        return subjects.findAllByOrderByNameAsc().stream().map(SubjectResponse::from).toList();
    }

    @Transactional
    public SubjectResponse update(UUID id, UpdateSubjectRequest request) {
        Subject subject = subjects.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Subject not found"));
        if (!subject.getName().equals(request.name()) && subjects.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.NAME_ALREADY_EXISTS, "Subject name already exists");
        }
        subject.setName(request.name());
        subject.setDescription(request.description());
        subject.setActive(request.active());
        return SubjectResponse.from(subjects.save(subject));
    }
}
