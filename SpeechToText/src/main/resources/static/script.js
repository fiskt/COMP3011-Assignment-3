const recordBtn = document.getElementById("record-btn");
const audioPlayer = document.getElementById("audio-player");
const audioContainer = document.querySelector(".audio-container");
const textOutput = document.querySelector(".text-output-box");

// Sends audio recording to Springboot backend
async function sendAudio(recordedBlob) {
     if (!recordedBlob) return;

    // FormData used since Springboot takes in MultipartFile
    const data = new FormData();
    data.append(
        "audio-file",       // Must match with the name in @RequestParam
        recordedBlob,       // Recorded audio data
        "recording.webm"    // File name
    );

    try {
        // Send data to the Java endpoint
        const response = await fetch("/api/v1/transcribe", {
            method: "POST",
            body: data
        });

        // Receive the transcription output and
        // display on the webpage
        var result = await response.text();
        textOutput.textContent = result;
    } catch (error) {
        textOutput.textContent = "Failed to transcribe audio.";
        console.error(error);
    }
}

// Store audio chunks while recording
let chunks = [];

// Stores the complete audio clip once recording stops
let recordedBlob = null;

// Created once user gives mic permissions
let mediaRecorder = null;

let isRecording = false;
const constraints = { audio: true };

recordBtn.addEventListener("click", async () => {
    if (!isRecording) {
        if (!mediaRecorder) {
            // Ask for mic access
            const stream = await navigator.mediaDevices.getUserMedia(constraints);

            // Create recorder that saves webm and save audio chunks once
            // recording starts
            mediaRecorder = new MediaRecorder(stream, { mimeType: "audio/webm" });
            mediaRecorder.addEventListener("dataavailable", (e) => {
                chunks.push(e.data);
            });

            // Runs when recording stops
            mediaRecorder.addEventListener("stop", async() => {
                console.log("onstop fired");

                // Combine the chunks and send it to the transcription endpoint
                recordedBlob = new Blob(chunks, { type: "audio/webm" });
                audioPlayer.src = window.URL.createObjectURL(recordedBlob);
                await sendAudio(recordedBlob);
            });

            mediaRecorder.addEventListener("error", (e) => {
                console.error("An error occurred:", e);
            });
        }

        // Change UI to show recording has started
        isRecording = true;
        audioContainer.classList.add("recording");
        recordBtn.textContent = "Stop";

        // Clear the old audio chunks and start the recorder
        chunks = [];
        mediaRecorder.start();
    } else {
        // Change UI to show recording has stopped
        isRecording = false;
        audioContainer.classList.remove("recording");
        recordBtn.textContent = "Record";

        // Stop the recorder, runs the "stop" event
        mediaRecorder.stop();
    }
})