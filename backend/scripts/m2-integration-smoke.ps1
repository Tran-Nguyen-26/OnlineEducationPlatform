param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$PostgresContainer = 'oep-postgres-1',
    [string]$DbName = 'oep',
    [string]$DbUser = 'oep',
    [string]$Password = 'SmokePass123!'
)

$ErrorActionPreference = 'Stop'
$createdContentIds = [System.Collections.Generic.List[string]]::new()
$ownerToken = $null
$courseId = $null
$moduleIds = @()
$lessonIds = @()
$ownerEmail = $null
$studentEmail = $null
$studentId = $null
$uploadFile = $null
$contentCleanupFailed = $false

function Invoke-Api([string]$Method, [string]$Path, [string]$Token, $Body) {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $request = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 30; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
    }
    try {
        $response = Invoke-WebRequest @request
        $status = [int]$response.StatusCode
        $raw = $response.Content
    }
    catch {
        $errorResponse = $_.Exception.Response
        if (-not $errorResponse) { throw }
        $status = [int]$errorResponse.StatusCode
        if ($errorResponse.PSObject.Methods.Name -contains 'GetResponseStream') {
            $reader = [System.IO.StreamReader]::new($errorResponse.GetResponseStream())
            try { $raw = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        else {
            $raw = [string]$errorResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        }
    }

    $data = $null
    if ($raw) {
        try { $data = ConvertFrom-Json -InputObject $raw } catch { }
    }
    return @{ Status = $status; Data = $data; Raw = $raw }
}

function Assert-Status($Result, [int]$Expected, [string]$Name) {
    if ($Result.Status -ne $Expected) {
        throw "$Name expected HTTP $Expected but received $($Result.Status): $($Result.Raw)"
    }
    Write-Output "PASS $Name (HTTP $Expected)"
}

function Assert-True([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "Assertion failed: $Name" }
    Write-Output "PASS $Name"
}

function Invoke-DatabaseSql([string]$Sql) {
    $Sql | docker exec -i $PostgresContainer psql -v ON_ERROR_STOP=1 -U $DbUser -d $DbName
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL cleanup/setup failed with exit code $LASTEXITCODE" }
}

try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 10
    if ($health.status -ne 'UP') { throw "API Gateway health is not UP: $($health.status)" }
    Write-Output 'PASS API Gateway health'

    $tag = [guid]::NewGuid().ToString('N').Substring(0, 12)
    $ownerEmail = "m28-owner-$tag@example.test"
    $studentEmail = "m28-student-$tag@example.test"

    $ownerRegistration = Invoke-Api POST '/api/auth/register' $null @{
        email = $ownerEmail; password = $Password; fullName = 'M2.8 Integration Owner'
    }
    Assert-Status $ownerRegistration 201 'register course owner'
    $ownerId = [string]$ownerRegistration.Data.user.id

    $studentRegistration = Invoke-Api POST '/api/auth/register' $null @{
        email = $studentEmail; password = $Password; fullName = 'M2.8 Integration Student'
    }
    Assert-Status $studentRegistration 201 'register student'
    $studentId = [string]$studentRegistration.Data.user.id

    Invoke-DatabaseSql "UPDATE users SET role='INSTRUCTOR' WHERE id='$ownerId';"
    $ownerLogin = Invoke-Api POST '/api/auth/login' $null @{ email = $ownerEmail; password = $Password }
    Assert-Status $ownerLogin 200 'login owner'
    $ownerToken = [string]$ownerLogin.Data.accessToken
    $studentLogin = Invoke-Api POST '/api/auth/login' $null @{ email = $studentEmail; password = $Password }
    Assert-Status $studentLogin 200 'login student'
    $studentToken = [string]$studentLogin.Data.accessToken

    $courseTitle = "M2.8 integration $tag"
    $course = Invoke-Api POST '/api/v1/courses' $ownerToken @{
        title = $courseTitle; description = 'Repeatable M2.1-M2.6 API integration smoke'; visibility = 'PUBLIC'
    }
    Assert-Status $course 201 'create course'
    $courseId = [string]$course.Data.id
    Assert-True ($course.Data.lifecycle_status -eq 'DRAFT') 'new course starts DRAFT'
    Assert-Status (Invoke-Api GET "/api/v1/courses/$courseId" $ownerToken $null) 200 'read course'
    $courseUpdate = Invoke-Api PATCH "/api/v1/courses/$courseId" $ownerToken @{
        title = "$courseTitle updated"; description = 'Course update verified'
    }
    Assert-Status $courseUpdate 200 'update course metadata'

    $moduleOne = Invoke-Api POST "/api/v1/courses/$courseId/modules" $ownerToken @{ title = 'Module one'; position = 0 }
    Assert-Status $moduleOne 201 'create first module'
    $moduleTwo = Invoke-Api POST "/api/v1/courses/$courseId/modules" $ownerToken @{ title = 'Module two'; position = 0 }
    Assert-Status $moduleTwo 201 'create second module'
    $moduleList = Invoke-Api GET "/api/v1/courses/$courseId/modules" $ownerToken $null
    Assert-Status $moduleList 200 'list modules'
    $defaultModules = @($moduleList.Data.items | Where-Object { $_.is_default })
    Assert-True ($defaultModules.Count -eq 1) 'course creation supplies exactly one default module'
    Assert-True ($defaultModules[0].position -eq 2) 'duplicate module positions shift the default module without losing order'
    $moduleOneCurrent = $moduleList.Data.items | Where-Object { $_.id -eq $moduleOne.Data.id } | Select-Object -First 1
    Assert-True ($moduleOneCurrent.position -eq 1) 'second duplicate insertion preserves first inserted module order'
    $moduleIds = @([string]$defaultModules[0].id, [string]$moduleOne.Data.id, [string]$moduleTwo.Data.id)
    Assert-Status (Invoke-Api GET "/api/v1/courses/$courseId/modules/$($moduleIds[0])" $ownerToken $null) 200 'read module'
    Assert-Status (Invoke-Api PATCH "/api/v1/courses/$courseId/modules/$($moduleIds[0])" $ownerToken @{ title = 'Default module updated' }) 200 'update module'
    $moduleOrder = Invoke-Api PATCH "/api/v1/courses/$courseId/module-order" $ownerToken @{ ordered_ids = @($moduleIds[2], $moduleIds[1], $moduleIds[0]) }
    Assert-Status $moduleOrder 200 'reorder modules'
    Assert-True ($moduleOrder.Data.ordered_ids[0] -eq $moduleIds[2]) 'module order persisted'

    $lessonOne = Invoke-Api POST "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons" $ownerToken @{ title = 'Lesson one'; position = 0 }
    Assert-Status $lessonOne 201 'create first lesson'
    $lessonTwo = Invoke-Api POST "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons" $ownerToken @{ title = 'Lesson two'; position = 0 }
    Assert-Status $lessonTwo 201 'create second lesson'
    $lessonIds = @([string]$lessonOne.Data.id, [string]$lessonTwo.Data.id)
    $lessonList = Invoke-Api GET "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons" $ownerToken $null
    Assert-Status $lessonList 200 'list lessons'
    $lessonOneCurrent = $lessonList.Data.items | Where-Object { $_.id -eq $lessonOne.Data.id } | Select-Object -First 1
    Assert-True ($lessonOneCurrent.position -eq 1) 'duplicate lesson position preserves insertion order'
    Assert-Status (Invoke-Api GET "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$($lessonIds[0])" $ownerToken $null) 200 'read lesson'
    Assert-Status (Invoke-Api PATCH "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$($lessonIds[0])" $ownerToken @{ title = 'Lesson one updated' }) 200 'update lesson'
    $lessonOrder = Invoke-Api PATCH "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lesson-order" $ownerToken @{ ordered_ids = @($lessonIds[1], $lessonIds[0]) }
    Assert-Status $lessonOrder 200 'reorder lessons'
    Assert-True ($lessonOrder.Data.ordered_ids[0] -eq $lessonIds[1]) 'lesson order persisted'
    $emptyLesson = Invoke-Api POST "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons" $ownerToken @{ title = 'Empty lesson'; position = 2 }
    Assert-Status $emptyLesson 201 'create empty lesson for deletion'
    $emptyLessonId = [string]$emptyLesson.Data.id
    Assert-Status (Invoke-Api DELETE "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$emptyLessonId" $ownerToken $null) 200 'delete empty lesson'
    Assert-Status (Invoke-Api GET "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$emptyLessonId" $ownerToken $null) 404 'deleted lesson is absent'

    $contentPath = "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$($lessonIds[0])/contents"
    $textContent = Invoke-Api POST $contentPath $ownerToken @{
        content_type = 'TEXT'; title = 'Integration text'; text_content = 'M2.8 content'; position = 0
    }
    Assert-Status $textContent 201 'create TEXT content'
    $textId = [string]$textContent.Data.id
    $createdContentIds.Add($textId)
    Assert-True ($textContent.Data.upload_status -eq 'AVAILABLE') 'TEXT content is immediately AVAILABLE'
    Assert-Status (Invoke-Api DELETE "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$($lessonIds[0])" $ownerToken $null) 409 'reject deleting lesson with contents'

    $linkContent = Invoke-Api POST $contentPath $ownerToken @{
        content_type = 'LINK'; title = 'Integration link'; external_url = 'https://example.test/original'; position = 0
    }
    Assert-Status $linkContent 201 'create LINK content'
    $linkId = [string]$linkContent.Data.id
    $createdContentIds.Add($linkId)
    Assert-Status (Invoke-Api GET "$contentPath/$linkId" $ownerToken $null) 200 'read LINK content'
    $linkUpdated = Invoke-Api PATCH "$contentPath/$linkId" $ownerToken @{
        title = 'Integration link updated'; external_url = 'https://example.test/updated'
    }
    Assert-Status $linkUpdated 200 'update LINK content'

    $fileBytes = [System.Text.Encoding]::UTF8.GetBytes("%PDF-M2.8-$tag")
    $file = Invoke-Api POST $contentPath $ownerToken @{
        content_type = 'PDF'; title = 'Integration PDF'; file_name = 'm28.pdf';
        mime_type = 'application/pdf'; file_size = $fileBytes.Length; position = 0
    }
    Assert-Status $file 201 'create file metadata'
    $fileId = [string]$file.Data.id
    $createdContentIds.Add($fileId)
    Assert-True ($file.Data.upload_status -eq 'PENDING') 'file content starts PENDING'
    $contentList = Invoke-Api GET $contentPath $ownerToken $null
    Assert-Status $contentList 200 'list contents after duplicate position inserts'
    $textContentCurrent = $contentList.Data.items | Where-Object { $_.id -eq $textId } | Select-Object -First 1
    $linkContentCurrent = $contentList.Data.items | Where-Object { $_.id -eq $linkId } | Select-Object -First 1
    Assert-True ($file.Data.position -eq 0 -and $linkContentCurrent.position -eq 1 -and $textContentCurrent.position -eq 2) 'duplicate content positions shift without losing order'

    $tooLarge = Invoke-Api POST $contentPath $ownerToken @{
        content_type = 'PDF'; title = 'Oversize'; file_name = 'large.pdf';
        mime_type = 'application/pdf'; file_size = 104857601
    }
    Assert-Status $tooLarge 413 'reject file over 100 MiB'
    $fileUpload = Invoke-Api POST "/api/v1/contents/$fileId/upload-url" $ownerToken $null
    Assert-Status $fileUpload 200 'issue signed PUT URL'
    Assert-True (-not $fileUpload.Data.upload_url.Contains($fileId)) 'upload URL does not expose the content ID as object key'
    Assert-True ($fileUpload.Data.required_headers.'Content-Type' -eq 'application/pdf') 'upload URL declares Content-Type'
    Assert-Status (Invoke-Api GET "/api/v1/contents/$fileId/access-url" $ownerToken $null) 409 'deny access while upload is pending'

    $uploadFile = Join-Path ([System.IO.Path]::GetTempPath()) "oep-m28-$tag.pdf"
    [System.IO.File]::WriteAllBytes($uploadFile, $fileBytes)
    $put = Invoke-WebRequest -Uri $fileUpload.Data.upload_url -Method Put -Headers @{ 'Content-Type' = 'application/pdf' } -InFile $uploadFile -TimeoutSec 30 -UseBasicParsing
    if ([int]$put.StatusCode -notin @(200, 204)) { throw "Storage PUT returned HTTP $($put.StatusCode)" }
    Write-Output "PASS upload file to S3-compatible storage (HTTP $([int]$put.StatusCode))"

    $completed = Invoke-Api POST "/api/v1/contents/$fileId/complete-upload" $ownerToken $null
    Assert-Status $completed 200 'verify upload metadata and complete file'
    Assert-True ($completed.Data.upload_status -eq 'AVAILABLE') 'verified file becomes AVAILABLE'
    Assert-Status (Invoke-Api GET $contentPath $ownerToken $null) 200 'list lesson contents'
    Assert-Status (Invoke-Api POST "/api/v1/courses/$courseId/publish" $ownerToken $null) 200 'publish ready course'
    Assert-Status (Invoke-Api GET "/api/v1/courses/$courseId/outline" $null $null) 200 'public published outline preview'
    $catalog = Invoke-Api GET '/api/v1/courses?page=1&page_size=100' $null $null
    Assert-Status $catalog 200 'read public course catalog'
    Assert-True (@($catalog.Data.items | Where-Object { $_.id -eq $courseId }).Count -eq 1) 'published course appears in catalog'

    Assert-Status (Invoke-Api GET "/api/v1/contents/$fileId/access-url" $studentToken $null) 403 'deny non-enrolled student access'
    $enrollmentId = [guid]::NewGuid().ToString()
    Invoke-DatabaseSql "INSERT INTO enrollments (id,course_id,student_id,status,created_via,enrolled_at) VALUES ('$enrollmentId','$courseId','$studentId','ACTIVE','M2_8_SMOKE',now());"
    Assert-Status (Invoke-Api GET "/api/v1/courses/$courseId/outline" $studentToken $null) 200 'allow ACTIVE student outline read'
    $access = Invoke-Api GET "/api/v1/contents/$fileId/access-url" $studentToken $null
    Assert-Status $access 200 'issue signed GET URL to ACTIVE student'
    Assert-True ($access.Data.method -eq 'GET' -and $access.Data.file_name -eq 'm28.pdf') 'signed GET metadata is correct'
    $download = [System.Net.WebClient]::new().DownloadData($access.Data.access_url)
    Assert-True ([Convert]::ToBase64String($download) -eq [Convert]::ToBase64String($fileBytes)) 'downloaded file bytes match uploaded bytes'

    Assert-Status (Invoke-Api POST "/api/v1/courses/$courseId/unpublish" $ownerToken $null) 200 'unpublish course'
    Assert-Status (Invoke-Api POST "/api/v1/courses/$courseId/publish" $ownerToken $null) 200 'republish course'
    Assert-Status (Invoke-Api POST "/api/v1/courses/$courseId/archive" $ownerToken $null) 200 'archive course'
    Assert-Status (Invoke-Api POST "/api/v1/courses/$courseId/restore" $ownerToken $null) 200 'restore course'

    Write-Output "M2 integration smoke passed. Course=$courseId Owner=$ownerEmail Student=$studentEmail"
}
finally {
    if ($ownerToken -and $createdContentIds.Count -gt 0 -and $courseId -and $moduleIds.Count -gt 0 -and $lessonIds.Count -gt 0) {
        $contentPath = "/api/v1/courses/$courseId/modules/$($moduleIds[0])/lessons/$($lessonIds[0])/contents"
        try {
            Invoke-DatabaseSql "DELETE FROM content_change_logs WHERE content_id IN (SELECT lc.id FROM lesson_contents lc JOIN lessons l ON l.id=lc.lesson_id JOIN course_modules m ON m.id=l.module_id WHERE m.course_id='$courseId');"
        }
        catch { Write-Warning "Content-history cleanup failed: $($_.Exception.Message)" }
        foreach ($contentId in $createdContentIds) {
            try {
                $delete = Invoke-Api DELETE "$contentPath/$contentId" $ownerToken $null
                if ($delete.Status -notin @(200, 404)) {
                    $contentCleanupFailed = $true
                    Write-Warning "API cleanup for content $contentId returned HTTP $($delete.Status)"
                }
            }
            catch {
                $contentCleanupFailed = $true
                Write-Warning "API cleanup for content $contentId failed: $($_.Exception.Message)"
            }
        }
    }

    if ($ownerEmail -or $studentEmail -or $courseId) {
        if ($contentCleanupFailed) {
            Write-Warning 'Database cleanup skipped to preserve object metadata after an API content-deletion failure.'
        }
        else {
        $ownerEmailSql = if ($ownerEmail) { "'$ownerEmail'" } else { "'m28-no-owner@example.test'" }
        $studentEmailSql = if ($studentEmail) { "'$studentEmail'" } else { "'m28-no-student@example.test'" }
        $courseIdSql = if ($courseId) { "'$courseId'" } else { "NULL" }
        $cleanupSql = @"
BEGIN;
CREATE TEMP TABLE m28_test_users AS SELECT id FROM users WHERE email IN ($ownerEmailSql,$studentEmailSql);
CREATE TEMP TABLE m28_test_courses AS SELECT id FROM courses WHERE id = $courseIdSql;
DELETE FROM enrollments WHERE course_id IN (SELECT id FROM m28_test_courses) OR student_id IN (SELECT id FROM m28_test_users);
DELETE FROM content_change_logs WHERE content_id IN (SELECT lc.id FROM lesson_contents lc JOIN lessons l ON l.id=lc.lesson_id JOIN course_modules m ON m.id=l.module_id WHERE m.course_id IN (SELECT id FROM m28_test_courses));
DELETE FROM lesson_contents WHERE lesson_id IN (SELECT l.id FROM lessons l JOIN course_modules m ON m.id=l.module_id WHERE m.course_id IN (SELECT id FROM m28_test_courses));
DELETE FROM lessons WHERE module_id IN (SELECT id FROM course_modules WHERE course_id IN (SELECT id FROM m28_test_courses));
DELETE FROM course_instructors WHERE course_id IN (SELECT id FROM m28_test_courses) OR instructor_id IN (SELECT id FROM m28_test_users);
DELETE FROM course_modules WHERE course_id IN (SELECT id FROM m28_test_courses);
DELETE FROM courses WHERE id IN (SELECT id FROM m28_test_courses);
DELETE FROM notifications WHERE user_id IN (SELECT id FROM m28_test_users);
DELETE FROM notification_preferences WHERE user_id IN (SELECT id FROM m28_test_users);
DELETE FROM email_notification_events WHERE user_id IN (SELECT id FROM m28_test_users);
DELETE FROM audit_logs WHERE actor_id IN (SELECT id FROM m28_test_users);
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM m28_test_users);
DELETE FROM auth_identities WHERE user_id IN (SELECT id FROM m28_test_users);
DELETE FROM user_profiles WHERE user_id IN (SELECT id FROM m28_test_users);
DELETE FROM users WHERE id IN (SELECT id FROM m28_test_users);
COMMIT;
DO `$verify$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE email IN ($ownerEmailSql,$studentEmailSql)) THEN
        RAISE EXCEPTION 'M2.8 smoke users remain after cleanup';
    END IF;
    IF EXISTS (SELECT 1 FROM courses WHERE id = $courseIdSql) THEN
        RAISE EXCEPTION 'M2.8 smoke course remains after cleanup';
    END IF;
END
`$verify$;
"@
        try { Invoke-DatabaseSql $cleanupSql } catch { Write-Warning "Database cleanup failed: $($_.Exception.Message)" }
        }
    }

    if ($uploadFile -and (Test-Path -LiteralPath $uploadFile)) { Remove-Item -LiteralPath $uploadFile -Force }
}
